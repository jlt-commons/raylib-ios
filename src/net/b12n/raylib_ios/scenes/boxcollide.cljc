(ns net.b12n.raylib-ios.scenes.boxcollide
  "A cube walked across a grid, and the boxes it touches turn red. Ported from
  raylib-jlt's `box_collisions` (zlib licence).

  The original looks from (11, 12, 11) at (0, 0.5, 0) through a 45 degree
  perspective camera. It draws a grid of 20, then five static boxes standing on
  the ground, then the lime player cube of side 2 at (px, 1, pz), each drawn by
  `cube!` (so `net.b12n.raylib-ios.soft3d/cube`'s default shading). A box is red while the
  player overlaps it and gray otherwise. The test is a 3D AABB overlap of which
  only x and z are tested, since everything stands on the ground:
  `|px - x| < 1 + s/2` and `|pz - z| < 1 + s/2`, both strict. It draws no sphere
  and nothing moves by itself. The projection is in software, by
  `net.b12n.raylib-ios.soft3d`.

  The original's keys, and what stands in for each:
  - D and A add and subtract 0.18 on x a frame, S and W add and subtract 0.18 on
    z. The original's keys are each their own axis, so its diagonal moves 0.18
    on both, which is about 1.41 times as fast. The stick keeps one speed
    instead, as nudge, splitscreen and freecam do: a diagonal scales both axes
    by 1 / sqrt 2 (`diagonal`), and that is the one deliberate difference.
    Nothing is held to the grid: the player can leave it.
  - A relative thumb-stick replaces the four keys. The press point is its
    centre, and a finger that lands in the 3D area starts it. An axis is on
    when the finger is further than `gesture/slop` from the centre along it:
    left is A, right is D, up the glass is W (world -z, forward) and down is S.
    `net.b12n.raylib-ios.stick` decides whose finger it is: a stick starts only on a finger
    that was not down the frame before, so a finger that was already down when
    the scene opened, or one that began under Back or above the 3D area, never
    does, even when another finger lands. It follows only that finger, by touch
    id (by nearness when the host gives none), and ends when it lifts, even if
    another is down. A rotation of the phone drops it. A tap moves nothing.
  - Nothing else in the original reads input.

  The painter orders whole boxes, face by face, so while the player overlaps a
  box one of the player's faces can paint over a face of that box where a depth
  buffer would sort them per pixel. The box is red then, and this is a known
  limit of `net.b12n.raylib-ios.soft3d`.

  Nothing reads a frame time, like the original: the speed is per update. The
  original's text line at the top is kept as a caption below Back: its
  \"COLLISION!\" while the player overlaps a box, and \"drag to move the
  player\" where the original says \"WASD move the player\". Both are measured
  in `dimensions` so the wider fits. The camera's fovy is the original's while the
  3D area is as wide as 800x450 and widened by `net.b12n.raylib-ios.soft3d/fit-camera` in a
  narrower one. A face with a corner behind the near plane is dropped whole.

  The state holds `:px` and `:pz`, `:stick` (the centre and the finger, or nil),
  `:n` (the finger count last frame), `:pts` and `:ids` (the touch points and
  touch ids of that frame, for telling a new finger from one already down) and
  `:screen`. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.soft3d :as s3]
            [net.b12n.raylib-ios.stick :as stick]))

(def speed "World units a frame for each key. The original's SPEED." 0.18)
(def player-size "The player cube's side. The original's PS." 2.0)
(def diagonal
  "What a diagonal's two axes are scaled by, 1 / sqrt 2, so that it moves at
  `speed` in all, as `net.b12n.raylib-ios.scenes.nudge`, splitscreen and freecam do."
  (/ 1.0 (Math/sqrt 2.0)))

(def boxes
  "The original's static obstacles: a centre `:x` `:z` on the ground and a side
  `:s`, in its order. The fourth overlaps the player at the spawn."
  [{:x 4.0
    :z 0.0
    :s 2.0}
   {:x -4.0
    :z 3.0
    :s 2.6}
   {:x 0.0
    :z -5.0
    :s 3.0}
   {:x 1.6
    :z 0.0
    :s 2.0}
   {:x -6.0
    :z -3.0
    :s 2.2}])

(def background-colour "The original's (230, 235, 245)." [230 235 245 255])
(def box-colour "GRAY" [130 130 130 255])
(def hit-colour "RED" [230 41 55 255])
(def player-colour "LIME" [0 228 48 255])
(def hint-colour "DARKGRAY" [80 80 80 255])
(def hit-caption-colour "MAROON" [190 33 55 255])

(def hint "drag to move the player")
(def hit-text "COLLISION!")

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

;; --- layout -----------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure: `net.b12n.raylib-ios.soft3d/field`,
  which has `:viewport`, `:aspect`, `:size`, `:pad` and `:text-y`."
  [metrics]
  (s3/field metrics))

(defn dimensions
  "`geometry` plus the text. `:caption` is `{:x :y :size}`, the place and size
  of the one line, and `:lines` is both texts the line can say, as `{:s :x :y
  :size}`, so a test can check they fit. The size is cut back, to 8 at the
  least, so that the wider covers no more than 0.92 of the width. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [widest (max (measure hint 100) (measure hit-text 100))
        {:keys [pad text-y size]
         :as geo} (s3/field metrics widest)
        caption {:x pad
                 :y text-y
                 :size size}]
    (assoc geo
           :caption caption
           :lines [(assoc caption :s hint) (assoc caption :s hit-text)])))

;; --- the collision ----------------------------------------------------------

(defn hits
  "The set of indexes into `boxes` that the player at `px`, `pz` overlaps: the
  original's `hit?`, `|px - x| < 1 + s/2` and `|pz - z| < 1 + s/2`."
  [px pz]
  (let [ph (/ player-size 2.0)]
    (into #{}
          (keep-indexed
           (fn [i {:keys [x z s]}]
             (let [reach (+ ph (/ s 2.0))]
               (when (and (< (abs (- px x)) reach)
                          (< (abs (- pz z)) reach))
                 i))))
          boxes)))

(defn caption-text
  "The line for `state`: \"COLLISION!\" while any box is hit, else `hint`."
  [{:keys [px pz]}]
  (if (seq (hits px pz)) hit-text hint))

(defn caption-colour
  "MAROON while a box is hit, else DARKGRAY, as the original's text."
  [{:keys [px pz]}]
  (if (seq (hits px pz)) hit-caption-colour hint-colour))

;; --- the picture ------------------------------------------------------------

(defn camera
  "The original's camera, (11, 12, 11) looking at (0, 0.5, 0) with fovy 45,
  fitted to `dims`' 3D area by `net.b12n.raylib-ios.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [11.0 12.0 11.0]
                  :target [0.0 0.5 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn grid-list
  "The grid of 20, spacing 1, as an unfinished draw list. The camera never
  moves, so it depends on the layout alone and a draw can keep it."
  [cam dims]
  (s3/grid [] (s3/view-proj cam (:viewport dims)) 20 1.0))

(defn scene-list
  "The finished draw list for `state`: `base` (the `grid-list`), the boxes, red
  where hit, and the player."
  [base state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))
        hit (hits (:px state) (:pz state))
        dl (reduce-kv (fn [dl i {:keys [x z s]}]
                        (s3/cube dl vp nil [x (/ s 2.0) z] s
                                 (if (contains? hit i) hit-colour box-colour)))
                      base
                      boxes)]
    (s3/finish (s3/cube dl vp nil [(:px state) (/ player-size 2.0) (:pz state)]
                        player-size player-colour))))

;; --- the frame --------------------------------------------------------------

(defn- free-point?
  "Whether a finger at `p` can be a stick: not under Back."
  [p]
  (not (gesture/in-back-region? p)))

(defn stick-keys
  "Which of the original's four keys `stick` stands for, as a set of `:a`, `:d`,
  `:w` and `:s`, for `metrics`. An axis is on when the finger is further than
  `gesture/slop` from the centre along it."
  [stick metrics]
  (if stick
    (let [slop (gesture/slop metrics)
          dx (- (double (first (:at stick))) (double (first (:centre stick))))
          dy (- (double (second (:at stick))) (double (second (:centre stick))))]
      (cond-> #{}
        (< dx (- slop)) (conj :a)
        (> dx slop) (conj :d)
        (< dy (- slop)) (conj :w)
        (> dy slop) (conj :s)))
    #{}))

(defn advance
  "One frame, as the original's loop body: `px` gains `speed` for D and loses it
  for A, `pz` gains it for S and loses it for W, the keys being the stick's
  (`stick-keys`), and both axes scaled by `diagonal` when both are on. A release
  frame with fewer than two points lifts everything, and its position is never
  read. A rotation of the phone drops the stick, whose pixels are the old
  screen's."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
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
                                 :start? #(gesture/in-rect? (:viewport dims) %)})
        down (stick-keys stick metrics)
        kx (+ (if (:d down) 1.0 0.0) (if (:a down) -1.0 0.0))
        kz (+ (if (:s down) 1.0 0.0) (if (:w down) -1.0 0.0))
        k (if (and (not (zero? kx)) (not (zero? kz))) diagonal 1.0)]
    (assoc state
           :screen screen
           :n (count points)
           :pts points
           :ids ids
           :stick stick
           :px (+ (:px state) (* speed k kx))
           :pz (+ (:pz state) (* speed k kz)))))

(defn- init [{:keys [metrics]}]
  [{:px 0.0
    :pz 0.0
    :stick nil
    :n 0
    :pts []
    :ids nil
    :screen (:screen metrics)}
   [[:scene/init :boxcollide]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :boxcollide]]])

(defn scene []
  {:id :boxcollide
   :title "Box Collisions"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
