(ns raylib.scenes.doom
  "A Doom-style ray caster you walk and shoot in on two thumbs, ported from
  raylib-jlt's `doom` (`doom.clj`, 613 lines), which is Michiel Borkent's
  (@borkdude) `examples/doom.clj` in babashka/ffi (MIT licence, see NOTICE),
  itself the technique Wolfenstein made famous: one ray per screen column, a
  DDA walk over a grid of characters, and the distance of the hit decides how
  tall that column's strip is. The projection and the rects are
  `raylib.scenes.doom.ray`, the level and the colours `raylib.scenes.doom.map`,
  the HUD, minimap and crosshair `raylib.scenes.doom.hud`. This namespace is the
  game: the state, the rules, the fingers and the layout.

  Mirrored from doom.clj, by line:
  - The level (lines 51-67), the start (2.5, 7.5) facing +x with a camera plane
    of 0.66 (lines 154-175), and the six imps (line 152).
  - The DDA, its perpendicular distance and the wall height `H / dist` (lines
    181-219, 234-252), the distance and side shade (lines 221-226), the z-buffer
    and the sprite pass with its 12 strips tested against it (lines 177, 271-326).
  - `rotate` (lines 328-338), `move` at 3.4 cells a second, sliding along walls by
    testing each axis alone (lines 340-351), `shoot` taking the nearest living
    imp inside a 0.985 cone (lines 353-373), the imps walking at 0.9 and biting
    within 0.9 for a point of health a frame each (lines 375-405), the muzzle
    flash of 0.06 seconds, the fps averaged over 0.4 seconds, and `dt` held to
    0.05 (lines 430-450). The order of a frame is the original's: look, move,
    shoot, imps, flash, fps.
  - The HUD (lines 526-562): HEALTH red under 40, KILLS, IMPS, SHOTS, the fps and
    the column count, the crosshair, YOU DIED; the minimap (lines 469-499) with
    its panel, a rect for each wall in its style's colour, a dot for each living
    imp, the player and a line along the heading. All at the original's 900 by
    560 window's positions, scaled to the view.

  Changed, and why:
  - **Columns: 180, the original's 450.** The phone runs scene code about 33
    times slower than jolt on the laptop, and on the laptop a frame has to take
    about 0.30 ms under jolt (the update, the build and the draw side's loops
    with the FFI stubbed) to hold 60 fps. That 0.30 is empirical, from the
    2026-10-03 device pass, and not 16.7 ms divided by 33, which is 0.50. 180 is the largest of the counts
    measured (100, 160, 180 and 200) whose worst pose fits. At
    1206 pixels wide a column is 6.7 pixels, where the original's 450 are 2.7.
    Measured under jolt on the laptop: 0.20 ms from the start, 0.29 ms at the
    worst of 2576 poses (each open cell's centre by 16 headings, with and without
    six imps in view) and 0.28 ms at the worst of 1500 random poses with both
    thumbs held and the flash on. At 100 columns the worst was 0.20 ms, at 160
    0.27 ms and at 200 0.31 ms.
  - **Flat strips for textured ones.** A wall column is one rect in its style's
    colour (the original's own minimap colours) times the original's shade, as the
    vertex colour multiplies the texel. The brick, stone, panel and circuit
    patterns, and the texture coordinate, are dropped. A run of columns that are
    the same rect in pixels is one rect, which draws what the columns did.
  - **Sprites as rects.** The imp is cut into the original's 12 strips, each tested
    against its middle column's wall. A strip draws the body, head and eyes its
    texel column shows, as up to three rects, instead of a texture.
  - **The picture keeps the original's 900 by 560 proportions**, which are not
    square pixels, scaled to the field's width and placed at its top. On a phone
    held upright the rest of the field is the thumbs' ground.

  Controls. The original's keys and mouse and what stands in for each:
  - W, A, S, D and the arrows: a relative thumb-stick (`raylib.stick`) that begins
    on a fresh press in the left half, in the lower half of the field. The press
    point is its centre, past `gesture/slop` from it the player moves that way,
    up the glass forward, as W and D would, through `move`. The stick is a unit
    vector, so one speed everywhere, where the original's keys add. The strafe
    is the original's, along the camera plane, so it is 0.66 of the forward speed.
  - The mouse delta and LEFT and RIGHT: a drag in the right half turns, 0.0022
    radians a pixel (line 413) times `900 / view width`, a drag right turning right.
  - Left click and SPACE: a finger that lands on the FIRE button fires, once for
    each landing, as `mouse-pressed?` and `key-pressed?` do. It works while the
    other thumb walks. A finger on the button never starts a stick or a turn.
  - A stick and a turn begin only on a fresh finger and follow only their own,
    by touch id, so a resting finger never steers (`raylib.stick`).
  - Dropped: `hide-cursor`, `set-mouse-position` and `show-cursor` (lines 410-420,
    the cursor warp), the quit key, and the title and window.

  The state holds the original's keys (`:pos-x :pos-y :dir-x :dir-y :plane-x
  :plane-y :imps :health :kills :shots :flash :fps :fps-frames :fps-elapsed`) and
  the fingers: `:look`, `:stick`, `:n`, `:pts`, `:ids` and `:screen`. Colours are
  `[r g b a]` vectors, and the rect buffer's are packed as `raylib.host/rgba`."
  (:require [raylib.gesture :as gesture]
            [raylib.scenes.doom.hud :as hud]
            [raylib.scenes.doom.map :as m]
            [raylib.scenes.doom.ray :as ray]
            [raylib.scenes.freecam :as free]
            [raylib.soft3d :as s3]
            [raylib.stick :as stick]))

(def window-w "The original's window width (line 42)." 900)
(def window-h "The original's window height (line 43)." 560)
(def mouse-sensitivity "Radians a pixel of the original's mouse (line 413)." 0.0022)
(def dt-cap "The original's longest frame (line 441), seconds." 0.05)
(def fps-window "The original's fps averaging window (line 435), seconds." 0.4)

(def background-colour "A dark ground for the field round the picture (the HUD-BG, line 456)." [16 14 18 255])
(def caption-colour "GRAY, as raylib defines it." [130 130 130 255])
(def caption-text "stick walks, drag turns, button fires")
(def fire-label "FIRE")

(def fire-colour [200 60 50 150])
(def fire-label-colour [245 245 245 255])
(def stick-ring-colour [240 240 240 70])
(def stick-knob-colour [240 240 240 150])

;; --- the world -----------------------------------------------------------------

(defn initial-state
  "The original's start (lines 154-175), without fingers."
  []
  {:pos-x 2.5
   :pos-y 7.5
   :dir-x 1.0
   :dir-y 0.0
   :plane-x 0.0
   :plane-y m/plane-length
   :imps (mapv (fn [[x y]] {:x x
                            :y y
                            :alive true})
               m/imp-spawns)
   :health 100
   :kills 0
   :shots 0
   :flash 0.0
   :fps 0.0
   :fps-frames 0
   :fps-elapsed 0.0})

(defn rotate
  "Turn the heading and the camera plane together by `a` radians (lines 328-338)."
  [s a]
  (let [c (Math/cos a)
        sn (Math/sin a)
        {:keys [dir-x dir-y plane-x plane-y]} s]
    (assoc s
           :dir-x (- (* dir-x c) (* dir-y sn))
           :dir-y (+ (* dir-x sn) (* dir-y c))
           :plane-x (- (* plane-x c) (* plane-y sn))
           :plane-y (+ (* plane-x sn) (* plane-y c)))))

(defn move
  "Walk `forward` along the heading and `strafe` along the camera plane at
  `m/move-speed` for `dt` seconds, sliding along walls by testing each axis on
  its own (lines 342-351)."
  [s {:keys [forward strafe dt]}]
  (let [{:keys [pos-x pos-y dir-x dir-y plane-x plane-y]} s
        sp (* m/move-speed dt)
        nx (+ pos-x (* sp (+ (* forward dir-x) (* strafe plane-x))))
        ny (+ pos-y (* sp (+ (* forward dir-y) (* strafe plane-y))))]
    (cond-> s
      (zero? (m/wall-at (int nx) (int pos-y))) (assoc :pos-x nx)
      (zero? (m/wall-at (int pos-x) (int ny))) (assoc :pos-y ny))))

(defn shoot
  "Hit the nearest living imp inside a narrow cone of the heading (lines 353-373)."
  [s]
  (let [{:keys [pos-x pos-y dir-x dir-y imps]} s
        s (-> s (update :shots inc) (assoc :flash m/flash-time))
        hit (->> imps
                 (keep-indexed
                  (fn [i imp]
                    (when (:alive imp)
                      (let [ex (- (:x imp) pos-x)
                            ey (- (:y imp) pos-y)
                            dist (Math/sqrt (+ (* ex ex) (* ey ey)))
                            dot (/ (+ (* ex dir-x) (* ey dir-y)) (max 0.001 dist))]
                        (when (> dot m/fire-cone) [i dist])))))
                 (sort-by second)
                 first)]
    (if hit
      (-> s
          (assoc-in [:imps (first hit) :alive] false)
          (update :kills inc))
      s)))

(defn- imp-distance
  [{:keys [pos-x pos-y]} imp]
  (let [ex (- pos-x (:x imp))
        ey (- pos-y (:y imp))]
    (Math/sqrt (+ (* ex ex) (* ey ey)))))

(defn- walk-imp
  "One imp step straight at the player, sliding along walls (lines 383-392)."
  [s imp dt]
  (let [d (imp-distance s imp)
        sp (* m/imp-speed dt)
        nx (+ (:x imp) (* sp (/ (- (:pos-x s) (:x imp)) d)))
        ny (+ (:y imp) (* sp (/ (- (:pos-y s) (:y imp)) d)))]
    (cond-> imp
      (zero? (m/wall-at (int nx) (int (:y imp)))) (assoc :x nx)
      (zero? (m/wall-at (int (:x imp)) (int ny))) (assoc :y ny))))

(defn advance-imps
  "Imps walk at the player. One already close enough stops and bites instead,
  costing a point of health per frame per imp (lines 394-405)."
  [s dt]
  (let [biting? (fn [imp] (and (:alive imp) (< (imp-distance s imp) m/bite-range)))]
    (-> s
        (update :imps
                (fn [imps]
                  (mapv #(if (or (not (:alive %)) (biting? %)) % (walk-imp s % dt))
                        imps)))
        (update :health - (count (filter biting? (:imps s))))
        (update :health #(max 0 %)))))

(defn track-fps
  "A frame rate averaged over roughly 0.4s (lines 430-437)."
  [s dt]
  (let [frames (inc (:fps-frames s))
        elapsed (+ (:fps-elapsed s) dt)]
    (if (> elapsed fps-window)
      (assoc s :fps (/ frames elapsed) :fps-frames 0 :fps-elapsed 0.0)
      (assoc s :fps-frames frames :fps-elapsed elapsed))))

(defn step
  "The world after one frame of input (`turn` radians, `forward` and `strafe`,
  `fire?`) and `dt` seconds, in the original's order (lines 407-450): look, move,
  shoot, then the imps while alive, the flash and the fps. `dt` is held to 0.05."
  [s {:keys [turn forward strafe fire? dt]}]
  (let [dt (min dt-cap (max 0.0 (double dt)))
        s (cond-> s
            (and turn (not (zero? turn))) (rotate turn)
            (or (not (zero? (or forward 0))) (not (zero? (or strafe 0))))
            (move {:forward (or forward 0)
                   :strafe (or strafe 0)
                   :dt dt})
            fire? shoot)
        s (if (pos? (:health s)) (advance-imps s dt) s)]
    (-> s
        (update :flash #(max 0.0 (- % dt)))
        (track-fps dt))))

;; --- layout --------------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`. `raylib.soft3d/field` gives the caption
  row under Back and the field, then

  - `:view`, `[x y w h]` in whole pixels: the original's 900 by 560 picture
    scaled by `:k` to fit the field, centred across it and at its top;
  - `:stick-top`, the y where a stick may begin (the field's lower half), in the
    left half of the field; a turn begins anywhere in the right half;
  - `:fire`, the button `{:cx :cy :r}` in the field's lower right corner;
  - `:stick-r`, the ring a held stick draws.

  With `widest`, the caption's width at size 100, the caption's size is cut back
  as `raylib.soft3d/field` does."
  ([metrics] (geometry metrics nil))
  ([metrics widest]
   (let [{:keys [pad]
          [fx fy fw fh] :viewport
          :as field} (s3/field metrics widest)
         k (min (/ fw (double window-w)) (/ fh (double window-h)))
         vw (int (* window-w k))
         vh (int (* window-h k))
         vx (int (+ fx (* 0.5 (- fw vw))))
         short-side (min fw fh)
         r (* 0.1 short-side)]
     (assoc field
            :k k
            :view [vx (int fy) vw vh]
            :stick-top (+ fy (* 0.5 fh))
            :stick-r (* 0.1 short-side)
            :fire {:cx (- (+ fx fw) pad r)
                   :cy (- (+ fy fh) pad r)
                   :r r}))))

(defn on-fire?
  "Is `pt` on the FIRE button of `dims`?"
  [{{:keys [cx cy r]} :fire} [px py]]
  (let [dx (- (double px) cx)
        dy (- (double py) cy)]
    (<= (+ (* dx dx) (* dy dy)) (* r r))))

(defn region
  "`:stick` for a point in the left half of the field from `:stick-top` down,
  `:look` for one in the right half, and nil for one outside the field, on the
  button, or in the left half above `:stick-top`."
  [{:keys [viewport stick-top]
    :as dims} [px py :as pt]]
  (let [[fx _ fw _] viewport]
    (when (and (gesture/in-rect? viewport pt) (not (on-fire? dims pt)))
      (if (< (double px) (+ fx (* 0.5 fw)))
        (when (>= (double py) stick-top) :stick)
        :look))))

(defn dimensions
  "`geometry` plus the text, with `measure` `(fn [s size] -> px)`: `:caption` and
  `:fire-label` as `{:s :x :y :size}`, `:hud` the widest HUD lines (999 health,
  kills and imps, 99999 shots, 999 fps) for a test, and `:lines` the caption, the
  label and those, so a test can check they fit. The caption's size is cut back,
  to 8 at the least, so that it covers no more than 0.92 of the width."
  [metrics measure]
  (let [{:keys [pad text-y size]
         {:keys [cx cy r]} :fire
         :as geo} (geometry metrics (measure caption-text 100))
        caption {:s caption-text
                 :x pad
                 :y text-y
                 :size size}
        label-size (max 8 (long (* 0.6 r)))
        label {:s fire-label
               :x (long (- cx (* 0.5 (measure fire-label label-size))))
               :y (long (- cy (* 0.5 label-size)))
               :size label-size}
        hud (hud/hud-lines {:health 999
                            :kills 999
                            :shots 99999
                            :imps (vec (repeat 6 {:alive true}))
                            :fps 999.0}
                           geo)]
    (assoc geo
           :caption caption
           :fire-label label
           :hud hud
           :lines (into [caption label] hud))))

;; --- the picture ---------------------------------------------------------------

(defn build!
  "Fill the rect buffer (`raylib.scenes.doom.ray/rect-buf`) with the picture of
  `state` in `dims`' view and return how many rects it holds."
  [state dims]
  (let [[vx vy vw vh] (:view dims)]
    (ray/build! state vx vy vw vh)))

(defn draw-rects!
  "Call `(f x y w h colour)` with longs and the packed colour for each of the
  first `n` rects of the buffer, in order. The gallery passes the host's
  rectangle call."
  [n f]
  (let [b ray/rect-buf
        palette m/palette]
    (loop [i 0]
      (when (< i n)
        (let [j (* 5 i)]
          (f (aget b j) (aget b (+ j 1)) (aget b (+ j 2)) (aget b (+ j 3)) (nth palette (aget b (+ j 4)))))
        (recur (inc i))))))

(defn stick-shape
  "A held stick's ring and knob as `{:centre :knob :r}`, the knob held within the
  ring, or nil."
  [{:keys [stick]} {:keys [stick-r]}]
  (when stick
    (let [[cx cy] (:centre stick)
          [ax ay] (:at stick)
          dx (- (double ax) cx)
          dy (- (double ay) cy)
          l (Math/sqrt (+ (* dx dx) (* dy dy)))
          f (if (> l stick-r) (/ stick-r l) 1.0)]
      {:centre [cx cy]
       :knob [(+ cx (* f dx)) (+ cy (* f dy))]
       :r stick-r})))

;; --- fingers -------------------------------------------------------------------

(defn advance
  "One frame. Fingers are sorted into a turn and a stick by `raylib.stick`, as in
  `raylib.scenes.fpmaze`: a stick or a turn begins only on a finger that was not
  down the frame before, inside its region, follows it by touch id, and ends when
  it lifts. A fresh finger on the FIRE button fires. A release frame with fewer
  than two points lifts everything, and its position is never read. A rotation of
  the phone drops the tracking, whose pixels are the old screen's."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc (dissoc state :look :stick) :n 0 :pts [] :ids nil)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        ids (stick/ids-of input points)
        frame {:points points
               :ids ids
               :metrics metrics
               :press? (= :press phase)
               :free? (constantly true)}
        [look stick] (stick/follow-pair (:look state) (:stick state) frame)
        fresh (stick/fresh frame state)
        {look' :look
         stick' :stick} (stick/begin-owners {:look look
                                             :stick stick}
                                            #(region dims %) fresh)
        dx (when (and look (:look state))
             (- (double (first (:at look))) (double (first (:at (:look state))))))
        [ux uy] (when stick' (free/stick-dir stick' metrics))
        view-w (nth (:view dims) 2)
        moved (step state {:turn (when dx (* dx mouse-sensitivity (/ (double window-w) view-w)))
                           :forward (when uy (- uy))
                           :strafe ux
                           :fire? (boolean (some #(on-fire? dims (:at %)) fresh))
                           :dt (or (:delta-seconds input) 0.0)})]
    (assoc moved
           :screen screen
           :n (count points)
           :pts points
           :ids ids
           :look look'
           :stick stick')))

(defn- init [{:keys [metrics]}]
  [(assoc (initial-state)
          :screen (:screen metrics)
          :n 0
          :pts []
          :ids nil
          :look nil
          :stick nil)
   [[:scene/init :doom]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :doom]]])

(defn scene []
  {:id :doom
   :title "Doom-like Raycaster"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
