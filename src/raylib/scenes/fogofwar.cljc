(ns raylib.scenes.fogofwar
  "A tile map under a fog that lifts where you walk. Ported from raylib-jlt's
  `fog-of-war` (`src/net/b12n/raylib_jlt/fog_of_war.clj`), itself a port of
  raylib's `textures_fog_of_war` (zlib licence).

  The original is a 25 by 15 map of 32 unit tiles (fog_of_war.clj lines 25-27,
  800 by 480 in all, drawn in an 800 by 450 window), two shades of blue picked
  at random per tile (line 73), with a 16 unit RED player (lines 28 and 127).
  Each frame every lit tile drops to remembered (`age-fog`, lines 47-51), then
  the tiles around the player's tile go lit (`light-around`, lines 53-63). The
  player's tile is `(int (/ (+ x 16) 32))` (`tile-of`, lines 42-45). The loops
  there run over `(range (- t 2) (+ t 2))`, which is t-2 to t+1: four tiles a
  side with one more behind than ahead, kept as it is. An unexplored tile is
  black, a remembered one is black at alpha 204, a lit one is clear (lines
  104-112), and the text, at (10, 10) and 25 above the window's bottom, is drawn
  last in RAYWHITE (lines 139-148).

  The original draws the fog into a render texture ONE PIXEL PER TILE and
  stretches it over the map, so the bilinear filter softens the edges. That
  texture is not bound here. Each tile is instead one gradient quad
  (`raylib.host/draw-gradient-quad`) whose four corners carry an alpha, and a
  corner's alpha is the mean of the four tiles that meet at it
  (`corner-alpha`), rounded to a whole alpha. Off the map's edge the texture's
  CLAMP wrap repeats the edge tile, so a corner on the border averages the tiles
  it has and a map corner takes its one tile. The difference from the texture is
  half a tile: bilinear filtering is exact at tile CENTRES and blends from
  centre to centre, where the quads are exact at tile CORNERS and blend across
  each tile. A lit tile among dark ones therefore fades out within its own
  square in the original and across the corners around it here, so the clear
  patch looks about half a tile smaller at its edge. The corner-averaged colour
  is still a bilinear surface, just sampled one half tile over. Nothing else
  about the fog changes.

  The original's keys, and what stands in for each:
  - The four arrow keys add and subtract SPEED 5 a frame on x and y (lines
    86-87), each axis on its own, so its diagonal moves 5 on both. A relative
    thumb-stick replaces them, as `raylib.scenes.boxcollide` does: the press is
    the centre, an axis is on when the finger is further than `gesture/slop`
    from it along that axis, and a diagonal scales both by 1 / sqrt 2
    (`diagonal`) so the stick moves at one speed. That is the one deliberate
    difference. `raylib.stick` decides whose finger it is, by touch id, so a
    finger that was down when the scene opened, or began under Back, never
    steers, and a rotation drops the stick. A tap moves nothing.
  - The player is held to the map, 0 to 784 and 0 to 464 (lines 89-93).
  - Until the first move the player walks the original's patrol loop (`patrol`,
    lines 32-40) so the fog lifts by itself, and `steered?` ends it for good
    (line 88).

  Nothing reads a frame time, like the original: a frame is one update. The map
  is drawn whole at one scale, the largest that fits below Back, so the strip of
  the bottom row that the original's 450 high window cuts off is shown here. The
  text keeps its place on the map: the tile line 10 units in, the hint 425 down.
  The hint says \"DRAG to move\" where the original says \"ARROW KEYS to move\",
  and \"patrolling until you drag to steer\" where it says \"patrolling until you
  press an arrow key\".

  The state holds `:px` `:py`, `:fog` (375 numbers, 0 unexplored, 2 remembered,
  1 lit), `:tiles` (375 shades, 0 or 1), `:frame`, `:steered?`, `:stick` (the
  centre, the finger and the id, or nil), `:n`, `:pts` and `:ids` (the fingers of
  the last frame) and `:screen`. The tile shades are the project's LCG's high
  bits. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.stick :as stick]))

(def tiles-x "TILES-X, line 25." 25)
(def tiles-y "TILES-Y, line 26." 15)
(def tile "TILE, line 27." 32)
(def player "PLAYER, line 28." 16)
(def visibility "VISIBILITY, line 29." 2)
(def speed "SPEED, line 30: units a frame." 5)
(def map-w "The map's width in units, TILES-X * TILE." (* tiles-x tile))
(def map-h "The map's height in units, TILES-Y * TILE." (* tiles-y tile))
(def diagonal
  "What a diagonal's two axes are scaled by, 1 / sqrt 2, so that it moves at
  `speed` in all, as `raylib.scenes.boxcollide` does."
  (/ 1.0 (Math/sqrt 2.0)))

(def seed "The LCG's start." 20261002)

(def background-colour "RAYWHITE" [245 245 245 255])
(def tile-a-colour "BLUE, a tile shade of 0 (line 119)." [0 121 241 255])
(def tile-b-colour "A tile shade of 1 (line 119)." [0 121 241 230])
(def outline-colour "The tile outline (line 125)." [0 82 172 128])
(def player-colour "RED" [230 41 55 255])
(def text-colour "RAYWHITE, the text (lines 139-148)." [245 245 245 255])

(def hint-patrol "Said until the first move." "patrolling until you drag to steer")
(def hint-steered "Said after it." "DRAG to move")

;; --- the fog ----------------------------------------------------------------

(defn patrol
  "Where the player stands on frame `n` while nobody is steering (lines 32-40)."
  [n]
  (let [t (* n 0.008)
        max-x (- map-w player)
        max-y (- map-h player)]
    [(* max-x (+ 0.5 (* 0.45 (Math/sin t))))
     (* max-y (+ 0.5 (* 0.45 (Math/sin (* 1.7 t)))))]))

(defn tile-of
  "The tile `[tx ty]` the player at `x` `y` stands on (lines 42-45)."
  [x y]
  [(int (/ (+ x (/ tile 2.0)) tile))
   (int (/ (+ y (/ tile 2.0)) tile))])

(defn age-fog
  "Everything currently lit drops to remembered (lines 47-51)."
  [fog]
  (mapv (fn [v] (if (= v 1) 2 v)) fog))

(defn light-around
  "Set the tiles of `fog` within `visibility` of `tx` `ty` to fully lit: x and y
  over t-2 to t+1, clipped to the map (lines 53-63)."
  [fog tx ty]
  (reduce (fn [f [x y]]
            (if (and (>= x 0) (< x tiles-x) (>= y 0) (< y tiles-y))
              (assoc f (+ x (* y tiles-x)) 1)
              f))
          fog
          (for [y (range (- ty visibility) (+ ty visibility))
                x (range (- tx visibility) (+ tx visibility))]
            [x y])))

(defn tile-alpha
  "The alpha of the fog over a tile in state `v`: 255 unexplored, 204
  remembered, 0 lit (lines 104-112)."
  [v]
  (case (long v)
    0 255
    2 204
    0))

(defn corner-alpha
  "The alpha at the map corner `cx` `cy` (0 to 25 by 0 to 15): the mean of the
  four tiles that meet there, rounded. A tile off the map is the edge tile
  beside it, as CLAMP wrap makes the texture do."
  [fog cx cy]
  (let [x0 (max 0 (dec cx))
        x1 (min (dec tiles-x) cx)
        y0 (max 0 (dec cy))
        y1 (min (dec tiles-y) cy)
        a (fn [x y] (tile-alpha (nth fog (+ x (* y tiles-x)))))]
    (long (+ 0.5 (/ (+ (a x0 y0) (a x1 y0) (a x0 y1) (a x1 y1)) 4.0)))))

(defn corner-alphas
  "`corner-alpha` for every corner, row by row, 26 by 16 of them."
  [fog]
  (let [alphas (mapv tile-alpha fog)
        a (fn [x y] (nth alphas (+ x (* y tiles-x))))]
    (into []
          (for [cy (range (inc tiles-y))
                cx (range (inc tiles-x))
                :let [x0 (max 0 (dec cx))
                      x1 (min (dec tiles-x) cx)
                      y0 (max 0 (dec cy))
                      y1 (min (dec tiles-y) cy)]]
            (long (+ 0.5 (/ (+ (a x0 y0) (a x1 y0) (a x0 y1) (a x1 y1)) 4.0)))))))

(defn tile-corners
  "The alphas at tile `x` `y`'s four corners in `alphas` (`corner-alphas`), as
  `[top-left top-right bottom-right bottom-left]`, the order
  `raylib.host/draw-gradient-quad` takes."
  [alphas x y]
  (let [stride (inc tiles-x)
        i (+ x (* y stride))]
    [(nth alphas i)
     (nth alphas (inc i))
     (nth alphas (+ i stride 1))
     (nth alphas (+ i stride))]))

;; --- the tile shades ----------------------------------------------------------

(defn- next-random [s]
  (mod (+ (* 1103515245 (long s)) 12345) 2147483648))

(defn tile-shades
  "The 375 tile shades, 0 or 1, from the LCG's high bits (line 73)."
  []
  (loop [i 0 s seed out []]
    (if (< i (* tiles-x tiles-y))
      (let [s' (next-random s)]
        (recur (inc i) s' (conj out (mod (quot s' 65536) 2))))
      out)))

;; --- layout -------------------------------------------------------------------

(defn dimensions
  "The layout for `metrics`' `:screen`. The map is `map-w` by `map-h` units at
  `:scale` pixels a unit, the largest that fits below Back, centred, at `:ox`
  `:oy` and `:map-w` `:map-h` pixels. The text keeps its place on the map, the
  tile line 10 units in and the hint 425 down (line 139 and the 450 high
  window's H - 25), at a size that is 20 units cut back, to 8 at the least, so
  the widest line stays inside the map. `:lines` is the three texts the two lines
  can say, as `{:s :x :y :size}`, so a test can check they fit. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        pad (max 8 (int (* 0.01 (min w h))))
        top (+ back-y back-h pad)
        fh (- h top pad)
        scale (min (/ w (double map-w)) (/ fh (double map-h)))
        mw (* scale map-w)
        mh (* scale map-h)
        ox (* 0.5 (- w mw))
        oy (+ top (* 0.5 (- fh mh)))
        inset (* 10.0 scale)
        tile-s "current tile: [24,14]"
        widest (max (measure tile-s 100) (measure hint-patrol 100) (measure hint-steered 100))
        size (max 8 (min (int (* 20.0 scale))
                         (int (/ (* 100.0 (- mw (* 2.0 inset))) widest))))
        x (+ ox inset)
        tile-line {:x x
                   :y (+ oy inset)
                   :size size}
        hint-line {:x x
                   :y (+ oy (* 425.0 scale))
                   :size size}]
    {:w w
     :h h
     :scale scale
     :ox ox
     :oy oy
     :map-w mw
     :map-h mh
     :tile-line tile-line
     :hint-line hint-line
     :lines [(assoc tile-line :s tile-s)
             (assoc hint-line :s hint-patrol)
             (assoc hint-line :s hint-steered)]}))

(defn tile-text
  "The tile line for `state` (line 139)."
  [{:keys [px py]}]
  (let [[tx ty] (tile-of px py)]
    (str "current tile: [" tx "," ty "]")))

(defn hint-text
  "The hint for `state` (lines 144-148)."
  [{:keys [steered?]}]
  (if steered? hint-steered hint-patrol))

;; --- the frame ----------------------------------------------------------------

(defn- free-point?
  "Whether a finger at `p` can be a stick: not under Back."
  [p]
  (not (gesture/in-back-region? p)))

(defn stick-keys
  "Which of the original's four arrow keys `stick` stands for, as a set of
  `:left`, `:right`, `:up` and `:down`, for `metrics`. An axis is on when the
  finger is further than `gesture/slop` from the centre along it."
  [stick metrics]
  (if stick
    (let [slop (gesture/slop metrics)
          dx (- (double (first (:at stick))) (double (first (:centre stick))))
          dy (- (double (second (:at stick))) (double (second (:centre stick))))]
      (cond-> #{}
        (< dx (- slop)) (conj :left)
        (> dx slop) (conj :right)
        (< dy (- slop)) (conj :up)
        (> dy slop) (conj :down)))
    #{}))

(defn- walk
  "The loop body (lines 86-95) with the keys already read as `dx` `dy`."
  [{:keys [px py fog frame steered?]
    :as state} dx dy]
  (let [steered? (or steered? (not (zero? dx)) (not (zero? dy)))
        [px py] (if steered?
                  [(-> (+ px dx) (max 0.0) (min (double (- map-w player))))
                   (-> (+ py dy) (max 0.0) (min (double (- map-h player))))]
                  (patrol frame))
        [tx ty] (tile-of px py)]
    (assoc state
           :px px
           :py py
           :steered? steered?
           :frame (inc frame)
           :fog (-> fog age-fog (light-around tx ty)))))

(defn advance
  "One frame: the stick's keys as `dx` `dy` of `speed` (scaled by `diagonal`
  when both axes are on), then the original's loop body. A release frame with
  fewer than two points lifts everything, and its position is never read. A
  rotation of the phone drops the stick, whose pixels are the old screen's."
  [state input]
  (let [metrics (:metrics input)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc (dissoc state :stick) :n 0 :pts [] :ids nil)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        ids (stick/ids-of input points)
        stick (stick/next-stick (:stick state) state
                                {:points points
                                 :ids ids
                                 :metrics metrics
                                 :press? (= :press phase)
                                 :free? free-point?
                                 :start? free-point?})
        down (stick-keys stick metrics)
        kx (+ (if (:right down) 1.0 0.0) (if (:left down) -1.0 0.0))
        ky (+ (if (:down down) 1.0 0.0) (if (:up down) -1.0 0.0))
        k (if (and (not (zero? kx)) (not (zero? ky))) diagonal 1.0)]
    (-> state
        (walk (* speed k kx) (* speed k ky))
        (assoc :screen screen
               :n (count points)
               :pts points
               :ids ids
               :stick stick))))

(defn- init [{:keys [metrics]}]
  [(-> {:px 180.0
        :py 130.0
        :fog (vec (repeat (* tiles-x tiles-y) 0))
        :tiles (tile-shades)
        :frame 0
        :steered? false
        :stick nil
        :n 0
        :pts []
        :ids nil
        :screen (:screen metrics)}
       (walk 0.0 0.0))
   [[:scene/init :fogofwar]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :fogofwar]]])

(defn scene []
  {:id :fogofwar
   :title "Fog of War"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
