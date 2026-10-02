(ns raylib.scenes.camera2d
  "A 2D camera that follows a player along a skyline. Ported from raylib-jlt's
  `camera2d`, which is raylib's `core_2d_camera` example (zlib licence).

  The original draws 30 buildings, a ground and a red player box through a
  Camera2D whose target follows the player on x. The arrow keys move the
  player 4 units a frame, the mouse wheel zooms by 0.05 a notch between 0.25
  and 3.0, A and D turn the camera a degree a frame, and R resets zoom and
  rotation. A grey line down the middle and a caption are drawn in screen space
  after the world. The buildings, their sizes and colours, the ground and the
  player are the original's formulas.

  Controls here:
  - A relative thumb-stick on x only replaces the left and right arrows. The
    press point is the centre. Further than `gesture/slop` to its left or right
    the player walks that way at the original's 4 world units a frame, frame
    locked, whatever the zoom. Inside the slop, or with the finger only
    drifting up or down, there is no direction, so a tap never moves it. The
    stick is on the glass, so after a twist the player still walks along the
    world's x, which is now tilted on screen, as the arrows did with A and D.
  - Two fingers replace the wheel and A and D. The distance between them
    scales the zoom and the twist of the line through them turns the camera
    (`camera2d/pinch-step`, so the order of the two points never matters). Both
    are about the field's centre, where the original's camera sits, and the
    zoom is held to 0.25 to 3.0 times the base zoom.
  - A \"reset\" button below Back replaces R and restores zoom 1 and rotation 0.
    The player stays where it is, as in the original.

  A pinch and the stick never share a finger. A second finger landing during a
  drag ends the stick at once, so the player stops, and while two fingers are
  down only the pinch acts, and only while exactly two are
  down (`camera2d/pinch-frame`), so a third finger or a reorder of three moves
  nothing. When one lifts, the finger left starts no new stick
  (a stick begins only at a press), so the player stays put until a fresh touch,
  and the next pair of fingers starts a new pinch from its first frame with no
  jump. A tap that ends a pinch is never read as a press on reset.

  The original's 800x450 view is fitted into the field below the caption by
  one base zoom, the smaller of the two axis scales, and the user's zoom
  multiplies that. The camera's offset is the field's centre. Its target is the
  player on x and 200 on y, as the original has it. The world is clipped to the
  field, and the centre line is drawn over it. The original's caption is
  replaced by one that names these controls. Nothing here reads a frame time,
  like the original. Colours are `[r g b a]` vectors."
  (:require [raylib.camera2d :as cam]
            [raylib.gesture :as gesture]))

(def player-speed "World units a frame. The original's." 4.0)
(def zoom-min "Smallest user zoom. The original's." 0.25)
(def zoom-max "Largest user zoom. The original's." 3.0)
(def ground-y "The ground's top in the world. The original's." 280)
(def target-y "The camera's target y. The original's." 200.0)
(def start-x "The player's starting x. The original's." 400.0)
(def view-w "The original's window width, fitted into the field." 800.0)
(def view-h "The original's window height, fitted into the field." 450.0)

(def background-colour [245 245 245 255])
(def ground-colour [130 130 130 255])
(def player-colour [230 41 55 255])
(def centre-line-colour [200 200 200 255])
(def hint-colour [80 80 80 255])
(def button-colour [200 200 200 255])
(def button-label-colour [80 80 80 255])

(def hint "drag: move - pinch: zoom - twist: rotate")
(def reset-label "reset")

(def buildings
  "The original's skyline: 30 buildings 60 apart along the ground."
  (mapv (fn [i]
          {:x (* i 60)
           :w (+ 30 (* 11 (mod (* (inc i) 7) 15)))
           :h (+ 60 (* 13 (mod (* (inc i) 5) 20)))
           :color [(+ 100 (mod (* i 37) 155))
                   (+ 80 (mod (* i 53) 120))
                   (+ 90 (mod (* i 29) 140))
                   255]})
        (range 30)))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure. `:reset` is the
  button `[x y w h]` below Back. `:field` is `[x y w h]`, the full width from
  below the hint line to the bottom, which the world is clipped to. `:offset`
  is its centre and `:base-zoom` fits the original's 800x450 view into it.
  `:size` is the text size the spacing is built on, the larger of 16 and 0.03 of
  the shorter side."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        bh (* 2.4 size)
        by (+ back-bottom pad)
        hint-y (+ by bh pad)
        ftop (+ hint-y size pad)
        fh (- h ftop)]
    {:w w
     :h h
     :size size
     :pad pad
     :reset [(double pad) (double by) (* 0.3 w) (double bh)]
     :hint-y hint-y
     :field [0.0 (double ftop) (double w) (double fh)]
     :offset [(* 0.5 w) (+ ftop (* 0.5 fh))]
     :base-zoom (min (/ w view-w) (/ fh view-h))}))

(defn dimensions
  "`geometry` plus the text: `:hint` and `:reset-label`, each `{:s :x :y
  :size}`, and `:lines` with both so a test can check they fit. The size is cut
  back from `geometry`'s when the hint would cover more than 0.92 of the width.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w size pad hint-y]
         [rx ry rw rh] :reset
         :as geo} (geometry metrics)
        widest (measure hint 100)
        size (max 8 (min size (int (/ (* 0.92 w 100.0) widest))))
        lines {:hint {:s hint
                      :x pad
                      :y (int hint-y)
                      :size size}
               :reset-label {:s reset-label
                             :x (int (+ rx (* 0.5 (- rw (measure reset-label size)))))
                             :y (int (+ ry (* 0.5 (- rh size))))
                             :size size}}]
    (assoc (merge geo lines)
           :text-size size
           :lines (vec (vals lines)))))

(defn clamp-zoom [z] (max zoom-min (min zoom-max z)))

(defn camera
  "The `raylib.camera2d` camera for `state` in `dims`: offset at the field's
  centre, target on the player, the base zoom times the user's."
  [state dims]
  {:offset (:offset dims)
   :target [(:px state) target-y]
   :rotation (:rot state)
   :zoom (* (:base-zoom dims) (:zoom state))})

(defn stick-dir
  "-1.0, 1.0 or nil. The stick's centre is where the touch began, kept in
  `(:stick state)`. While the finger is down and further than `gesture/slop`
  from it along x, the direction is the sign of that offset. The press frame, a
  finger inside the dead zone, no centre, a lifted finger and two or more
  fingers all give nil. `metrics` is the input's `:metrics`."
  [state input metrics]
  (let [centre (get-in state [:stick :centre])
        {:keys [phase position]} (:pointer input)]
    (when (and centre
               (< (count (:touch-points input)) 2)
               (gesture/down? input)
               (not= :press phase))
      (let [dx (- (double (first position)) (double (first centre)))]
        (when (> (abs dx) (gesture/slop metrics))
          (if (pos? dx) 1.0 -1.0))))))

(defn- next-stick
  "The stick after this frame: a single press that is neither under Back nor on
  the reset button starts one at the press point, a held finger keeps its
  centre, and anything else, two fingers included, ends it."
  [state input dims]
  (let [{:keys [phase position]} (:pointer input)]
    (when (< (count (:touch-points input)) 2)
      (case phase
        :press (when (and position
                          (not (gesture/in-back-region? position))
                          (not (gesture/in-rect? (:reset dims) position)))
                 {:centre position})
        :down (when-let [centre (get-in state [:stick :centre])]
                (when position {:centre centre}))
        nil))))

(defn advance
  "One frame. Calls `gesture/track` once. Two or more touch points drop the
  stick. Exactly two, after exactly two the frame before (`cam/pinch-frame`),
  pinch: the zoom is multiplied by `:ratio` and held to the clamp and the
  rotation grows by `:twist`. Any change of finger count only records, so a
  third finger, a lifted one or a reordered three moves nothing. Otherwise the stick moves
  the player `player-speed` world units. A tap on reset restores zoom and
  rotation unless it ended a pinch. A rotation of the phone drops the stick and
  the pinch, whose pixels are the old screen's. The release position is never
  read."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state)) (dissoc state :stick :pinch) state)
        points (vec (:touch-points input))
        pinching? (>= (count points) 2)
        {now :pinch
         step :step} (cam/pinch-frame (:pinch state) points)
        [g event] (gesture/track (:gesture state) input)
        reset? (and (= :tap (:type event))
                    (not pinching?)
                    (not (:pinch state))
                    (gesture/in-rect? (:reset dims) (:at event)))
        dir (stick-dir state input metrics)
        zoom (cond-> (:zoom state)
               step (-> (* (:ratio step)) clamp-zoom))
        rot (cond-> (:rot state)
              step (+ (:twist step)))]
    (assoc state
           :screen screen
           :gesture g
           :px (+ (:px state) (* (or dir 0.0) player-speed))
           :zoom (if reset? 1.0 zoom)
           :rot (if reset? 0.0 rot)
           :pinch now
           :stick (next-stick state input dims))))

(defn- init [{:keys [metrics]}]
  [{:px start-x
    :zoom 1.0
    :rot 0.0
    :screen (:screen metrics)
    :gesture gesture/idle
    :pinch nil
    :stick nil}
   [[:scene/init :camera2d]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :camera2d]]])

(defn scene []
  {:id :camera2d
   :title "2D Camera"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
