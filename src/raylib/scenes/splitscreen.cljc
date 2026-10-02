(ns raylib.scenes.splitscreen
  "Two players on one shared grid, each in a half of the screen. Ported from
  raylib-jlt's `camera-2d-split-screen`, which is raylib's
  `core_2d_camera_split_screen` example (zlib licence).

  The original draws a 20 by 11 grid of 40-unit cells with a `[i,j]` label in
  each, and two 40-unit squares on it, player one red at (200, 200) and player
  two blue at (250, 200). Each player has a Camera2D whose offset is (200, 200)
  and whose target is that player, rendered into a 400 by 440 render texture.
  The two textures sit side by side with a 4 pixel divider between them. Each
  half has a banner (RAYWHITE at 60% alpha, 30 high) with 10 point text naming
  the keys. W, A, S, D move player one and the arrow keys move player two, 3
  units a frame, frame locked, and the players are not held inside the grid.

  Here the render textures become a scissor and a camera: each half is a
  scissored viewport drawn through `raylib.host/with-camera-2d`, and the same
  world is drawn once per half. The halves stack in portrait (player one on
  top) and sit side by side in landscape, picked by the field's aspect. The
  camera's offset is its viewport's centre and one base zoom fits the original's
  400 by 440 half into either shape. The labels and the banner text keep the
  original's size 10, so they are scaled by that zoom with the rest of the
  world, and the banner's text size is the original's 10 times the zoom.

  Controls here, in place of the two sets of keys:
  - A relative thumb-stick in each half. A touch point steers the half it lies
    in, from `:touch-points`, which carries no ids and no stable order, so a
    point is assigned by where it is. A half's stick starts on the frame a point
    first appears in that half when there was none there the frame before, and
    its centre is where that point was. While a point stays in the half it is
    the stick's finger. Inside `gesture/slop` of the centre there is no
    direction, so a tap never moves a player. Outside it the player moves the
    original's 3 units a frame along the normalised vector, so a diagonal is no
    faster (the keys add two axes, 4.24 a frame, the one deliberate
    difference). A stick ends when its half has no point.
  - Two thumbs work at once, one per half. If two points are in one half, the
    stick follows the one nearest its previous finger, so it does not jump to
    a stranger, and a fresh stick takes the lesser point by x then y so the
    order of `:touch-points` never matters.
  - A thumb that slides across the divider ends the stick it left, and starts a
    fresh one in the other half at the crossing, so nothing jumps. If that half
    already has a thumb, the stick takes whichever point is nearer its finger.
  - A touch under `gesture/back-region` belongs to the host and is ignored, as
    if it were not there. Only `:touch-points` is read, so a release position
    never is. A rotation of the phone drops both sticks, whose pixels are the
    old screen's, and a finger held through it starts a fresh one where it now
    is, so nothing moves. This calls no `gesture/track`, since nothing here is a tap
    or a drag by itself.

  The original's banner texts name the keys, so they are replaced by
  \"PLAYER1: drag to move\" and \"PLAYER2: drag to move\", and their size shrinks
  when a banner would not fit its half. Nothing here reads a frame time, like
  the original. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]))

(def player-speed "World units a frame. The original's." 3.0)
(def cols "Grid columns. The original's." 20)
(def rows "Grid rows. The original's." 11)
(def cell "A grid cell's side, also a player's. The original's." 40)
(def world-w "The grid's width in the world." (* cols cell))
(def world-h "The grid's height in the world." (* rows cell))
(def half-w "The original's render texture width, fitted into a half." 400.0)
(def half-h "The original's render texture height, fitted into a half." 440.0)
(def divider-w "The gap between the halves. The original's." 4.0)
(def start-positions
  "Where the players start. The original's."
  [[200.0 200.0] [250.0 200.0]])

(def background-colour [245 245 245 255])
(def outer-colour [0 0 0 255])
(def grid-colour [200 200 200 255])
(def divider-colour [200 200 200 255])
(def player-colours [[230 41 55 255] [0 121 241 255]])
(def banner-colour [245 245 245 153])
(def banner-text-colours [[190 33 55 255] [0 82 172 255]])

(def banner-texts ["PLAYER1: drag to move" "PLAYER2: drag to move"])

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure. `:field` is
  `[x y w h]`, the full width from a gap below Back to the bottom. `:portrait?`
  is whether the field is taller than wide, when the halves stack. `:halves`
  are the two `[x y w h]` viewports, player one first, and `:divider` is the
  gap between them. `:offsets` are the viewports' centres and `:base-zoom` fits
  the original's 400 by 440 half into one."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        ftop (+ back-bottom pad)
        fh (- h ftop)
        fx 0.0
        fw (double w)
        portrait? (< fw fh)
        ftop (double ftop)
        fh (double fh)
        [a b divider]
        (if portrait?
          (let [hh (* 0.5 (- fh divider-w))]
            [[fx ftop fw hh]
             [fx (+ ftop hh divider-w) fw hh]
             [fx (+ ftop hh) fw divider-w]])
          (let [hw (* 0.5 (- fw divider-w))]
            [[fx ftop hw fh]
             [(+ hw divider-w) ftop hw fh]
             [hw ftop divider-w fh]]))
        [_ _ hw hh] a
        centre (fn [[x y rw rh]] [(+ x (* 0.5 rw)) (+ y (* 0.5 rh))])]
    {:w w
     :h h
     :field [fx ftop fw fh]
     :portrait? portrait?
     :halves [a b]
     :divider divider
     :offsets [(centre a) (centre b)]
     :base-zoom (min (/ hw half-w) (/ hh half-h))}))

(defn dimensions
  "`geometry` plus the banners: `:banners`, one per half, each `{:rect :s :x :y
  :size}` where `:rect` is the banner's `[x y w h]` at the top of its half and
  `x`, `y` are the text's origin, and `:lines` with the same two so a test can
  check they fit. The text size is the original's 10 times the base zoom, cut
  back when the wider text would not fit its half with the original's margin.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [halves base-zoom]
         :as geo} (geometry metrics)
        [_ _ hw _] (first halves)
        widest (apply max (map #(measure % 100) banner-texts))
        fit (int (/ (* 0.92 hw) (+ 1.0 (/ widest 100.0))))
        size (max 6 (min (max 8 (int (* 10 base-zoom))) fit))
        banners (mapv (fn [[hx hy bw _] s]
                        {:rect [hx hy bw (* 3.0 size)]
                         :s s
                         :x (int (+ hx size))
                         :y (int (+ hy size))
                         :size size})
                      halves banner-texts)]
    (assoc geo
           :text-size size
           :banners banners
           :lines banners)))

(defn camera
  "The `raylib.camera2d` camera for player `i` (0 or 1) in `state` and `dims`:
  offset at its viewport's centre, target on the player, the base zoom."
  [state dims i]
  {:offset (nth (:offsets dims) i)
   :target (nth (:players state) i)
   :rotation 0.0
   :zoom (:base-zoom dims)})

(defn half-of
  "0 or 1: the half of `dims` the scene-pixel point `pt` lies in. The divider
  belongs to the second half, and the halves extend to infinity across their
  shared axis, so a point just outside the field still has a half."
  [{:keys [portrait? divider]} [px py]]
  (let [[dx dy] divider]
    (if (if portrait?
          (< py (+ dy (* 0.5 divider-w)))
          (< px (+ dx (* 0.5 divider-w))))
      0
      1)))

(defn stick-dir
  "The unit vector `[dx dy]` a half's `stick` points, or nil. The stick holds
  `:centre` and `:finger`. Inside `gesture/slop` of the centre, or with no stick
  (nil), there is no direction. `metrics` is the input's `:metrics`."
  [stick metrics]
  (when stick
    (let [[cx cy] (:centre stick)
          [fx fy] (:finger stick)
          vx (- (double fx) (double cx))
          vy (- (double fy) (double cy))
          len (Math/sqrt (+ (* vx vx) (* vy vy)))]
      (when (> len (gesture/slop metrics))
        [(/ vx len) (/ vy len)]))))

(defn- d2 [[ax ay] [bx by]]
  (let [dx (- (double ax) (double bx))
        dy (- (double ay) (double by))]
    (+ (* dx dx) (* dy dy))))

(defn- pick
  "The point of `pts` nearest `from`, the lesser by x then y on a tie, so the
  answer does not depend on the order of `pts`."
  [from pts]
  (reduce (fn [best p]
            (let [db (d2 from best)
                  dp (d2 from p)]
              (if (or (< dp db)
                      (and (== dp db)
                           (let [[bx by] best
                                 [px py] p]
                             (or (< px bx) (and (== px bx) (< py by))))))
                p
                best)))
          (first pts)
          (rest pts)))

(defn- lesser
  "The lesser of `pts` by x then y."
  [pts]
  (reduce (fn [best [px py :as p]]
            (let [[bx by] best]
              (if (or (< px bx) (and (== px bx) (< py by))) p best)))
          (first pts)
          (rest pts)))

(defn- next-stick
  "A half's stick after this frame, given the points `pts` in it. No point ends
  it. With none before, the lesser point is the centre and the finger. Otherwise
  the centre stays and the finger is the point nearest the old finger."
  [stick pts]
  (cond
    (empty? pts) nil
    (nil? stick) (let [p (lesser pts)] {:centre p
                                        :finger p})
    :else {:centre (:centre stick)
           :finger (pick (:finger stick) pts)}))

(defn advance
  "One frame. Each touch point not under Back goes to the half it lies in, each
  half's stick follows `next-stick`, and each player moves `player-speed` world
  units along its stick's direction. A rotation of the phone drops both sticks
  first. The release position is never read, since only `:touch-points` is."
  [state input]
  (let [metrics (:metrics input)
        screen (:screen metrics)
        dims (geometry metrics)
        sticks (if (= screen (:screen state)) (:sticks state) [nil nil])
        points (remove gesture/in-back-region? (:touch-points input))
        by-half (reduce (fn [acc p] (update acc (half-of dims p) conj p))
                        [[] []]
                        points)
        sticks (mapv next-stick sticks by-half)
        players (mapv (fn [[x y] stick]
                        (let [[dx dy] (or (stick-dir stick metrics) [0.0 0.0])]
                          [(+ x (* dx player-speed)) (+ y (* dy player-speed))]))
                      (:players state)
                      sticks)]
    (assoc state
           :screen screen
           :sticks sticks
           :players players)))

(defn- init [{:keys [metrics]}]
  [{:players start-positions
    :sticks [nil nil]
    :screen (:screen metrics)}
   [[:scene/init :splitscreen]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :splitscreen]]])

(defn scene []
  {:id :splitscreen
   :title "2D Split Screen"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
