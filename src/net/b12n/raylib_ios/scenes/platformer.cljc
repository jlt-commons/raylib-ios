(ns net.b12n.raylib-ios.scenes.platformer
  "A 2D platformer with five ways for the camera to follow the player. Ported
  from raylib-jlt's `camera-2d-platformer`, which is raylib's
  `core_2d_camera_platformer` example (zlib licence).

  The original has a red 40 by 40 player, a sky and a floor and three one-way
  platforms. The arrow keys walk at 200 units a second, SPACE jumps at 350
  while standing on something, gravity is 400, C cycles the camera through
  five modes and R puts the player and camera back. The world, the physics and
  the five modes are the original's, and the physics use `:delta-seconds` as it
  uses `get-frame-time`, with no clamp: the collision test only asks whether
  this frame's fall crosses a top edge, so a long stalled frame (checked at 0.1
  s) still lands and nothing tunnels.

  Controls here are five buttons along the bottom, read from the input's
  `:touch-points` so two can be held at once, as in `net.b12n.raylib-ios.scenes.asteroids`:
  - \"<\" and \">\" walk left and right while held, in place of the arrows
    (the default font has no triangle glyphs). Both held cancel out, as both
    arrows did.
  - \"jump\" jumps while held and standing, in place of SPACE, which the
    original reads with `key-down?` too, so holding it hops again on landing.
  - \"camera\" steps to the next mode on a press, in place of C.
  - \"reset\" restores the player and camera on a press, in place of R. The
    mode is kept, as in the original.
  A press is a button that was not held on the frame before, so holding
  neither repeats nor re-fires, and a finger that slides on from another
  button counts. The original's key-help line is dropped, since the buttons
  name themselves, and a line above the field names the mode instead.

  The original's 800x450 view is fitted into the field between that line and
  the buttons by one base zoom, the smaller of the two axis scales, and it is
  the camera's zoom. Every mode's `W` and `H` becomes the field's own width
  and height in pixels after that zoom, so the camera's offset is the field's
  centre, the clamped mode tests the map's corners against the field's edges
  (through `net.b12n.raylib-ios.camera2d/world->screen`) and the push mode's dead zone is
  the middle 60 percent of the field. Distances in world units (the smooth
  mode's 10 unit rest and 30 unit floor, the even-out mode's 1 unit snap) are
  unchanged. The camera's offset is kept in the field's own pixels, and
  `camera` moves it to scene pixels. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.camera2d :as cam]
            [net.b12n.raylib-ios.gesture :as gesture]))

(def gravity "Units a second squared. The original's." 400.0)
(def jump-speed "Upward speed of a jump. The original's." 350.0)
(def walk-speed "Units a second. The original's." 200.0)
(def view-w "The original's window width, fitted into the field." 800.0)
(def view-h "The original's window height, fitted into the field." 450.0)

(def start-player
  "The player's start. The original's."
  {:x 400.0
   :y 280.0
   :speed 0.0
   :can-jump? false})

(def start-target "The camera's starting target. The original's." [400.0 280.0])

(def env-items
  "`[x y w h blocking? [r g b]]`: the sky, the floor and three platforms. The
  original's."
  [[0.0 0.0 1000.0 400.0 false [200 200 200]]
   [0.0 400.0 1000.0 200.0 true [130 130 130]]
   [300.0 200.0 400.0 10.0 true [130 130 130]]
   [250.0 300.0 100.0 10.0 true [130 130 130]]
   [650.0 300.0 100.0 10.0 true [130 130 130]]])

(def modes
  "`[id description]` for each camera mode, in the order C cycles them. The
  original's."
  [[:centre "follow player centre"]
   [:clamped "follow centre, clamped to the map edges"]
   [:smooth "follow centre, smoothed"]
   [:even-out "follow horizontally, ease vertically after landing"]
   [:push "player pushes the camera at the screen edge"]])

(defn mode-id [i] (first (nth modes i)))

(def background-colour [200 200 200 255])
(def player-colour [230 41 55 255])
(def text-colour [80 80 80 255])
(def button-colour [130 130 130 255])
(def button-held-colour [80 80 80 255])
(def button-label-colour [245 245 245 255])

(def button-labels
  "Each button's id and the label drawn on it, in left-to-right order."
  [[:left "<"] [:right ">"] [:jump "jump"] [:camera "camera"] [:reset "reset"]])

(defn mode-line
  "The line that names mode `i`, as the original's caption does."
  [i]
  (str "mode " (inc i) "/" (count modes) ": " (second (nth modes i))))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure. `:buttons` are the
  five `{:id :rect}` along the bottom, `:field` is `[x y w h]`, the full width
  between the mode line and the buttons, `:base-zoom` fits the original's
  800x450 view into it, and `:size` is the text size the spacing is built on."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        gap (* 0.02 side)
        bh (* 0.14 side)
        by (- h gap bh)
        bw (/ (- w (* (inc (count button-labels)) gap)) (count button-labels))
        text-y (+ back-bottom pad)
        ftop (+ text-y size pad)
        fh (- by gap ftop)]
    {:w w
     :h h
     :size size
     :pad pad
     :text-y text-y
     :buttons (vec (map-indexed
                    (fn [i [id _]]
                      {:id id
                       :rect [(+ gap (* i (+ bw gap))) by bw bh]})
                    button-labels))
     :field [0.0 (double ftop) (double w) (double fh)]
     :base-zoom (min (/ w view-w) (/ fh view-h))}))

(defn dimensions
  "`geometry` plus the text. `:mode-lines` are the five captions, each `{:s :x
  :y :size}`, all at one size, which is cut back from `geometry`'s when the
  widest would cover more than 0.92 of the width. `:lines` is the same list so
  a test can check them. Each of `:buttons` gains `:label`, `:label-size`,
  `:label-x` and `:label-y`, the size cut back until the widest label fits
  inside a button. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w size pad text-y]
         :as geo} (geometry metrics)
        widest (apply max (map #(measure (mode-line %) 100) (range (count modes))))
        size (max 8 (min size (int (/ (* 0.92 w 100.0) widest))))
        lines (mapv (fn [i] {:s (mode-line i)
                             :x pad
                             :y (int text-y)
                             :size size})
                    (range (count modes)))
        [_ _ bw bh] (:rect (first (:buttons geo)))
        wide-label (apply max (map #(measure (second %) 100) button-labels))
        label-size (max 8 (min (max 16 (int (* 0.034 (min w (:h geo)))))
                               (int bh)
                               (int (/ (* 0.9 bw 100.0) wide-label))))]
    (assoc geo
           :text-size size
           :mode-lines lines
           :lines lines
           :buttons (vec (map (fn [{[bx by] :rect
                                    :as b} [_ label]]
                                (assoc b
                                       :label label
                                       :label-size label-size
                                       :label-x (int (+ bx (* 0.5 (- bw (measure label label-size)))))
                                       :label-y (int (+ by (* 0.5 (- bh label-size))))))
                              (:buttons geo) button-labels)))))

(defn held-buttons
  "The set of button ids with any of the input's `:touch-points` inside."
  [{:keys [buttons]} input]
  (let [pts (:touch-points input)]
    (into #{}
          (keep (fn [{:keys [id rect]}]
                  (when (some (fn [p] (gesture/in-rect? rect p)) pts) id)))
          buttons)))

(defn update-player
  "The original's physics exactly, including the one-way platform rule: a
  blocking item stops the player only when this frame's fall crosses its top
  edge. `held` is the set of held button ids and `dt` is in seconds."
  [{:keys [x y speed]
    :as p} held dt]
  (let [x (cond-> x
            (contains? held :left) (- (* walk-speed dt))
            (contains? held :right) (+ (* walk-speed dt)))
        [speed jumped?] (if (and (contains? held :jump) (:can-jump? p))
                          [(- jump-speed) true]
                          [speed false])
        hit (some (fn [[ex ey ew _ blocking? _]]
                    (when (and blocking?
                               (<= ex x) (>= (+ ex ew) x)
                               (>= ey y) (<= ey (+ y (* speed dt))))
                      ey))
                  env-items)]
    (if (and hit (not jumped?))
      (assoc p :x x :y hit :speed 0.0 :can-jump? true)
      (assoc p :x x
             :y (+ y (* speed dt))
             :speed (+ speed (* gravity dt))
             :can-jump? false))))

(defn update-camera
  "The camera after `mode` has followed `player` for `dt` seconds. `cam` is
  `{:offset :target}` with the offset in the field's own pixels; the result has
  the same keys. The offset starts each frame at the field's centre, then the
  mode may move it. See the namespace docstring for how `W` and `H` became the
  field's size."
  [mode cam {:keys [x y]} dt {:keys [field base-zoom]}]
  (let [[_ _ vw vh] field
        half-w (/ vw 2.0)
        half-h (/ vh 2.0)
        cam (assoc cam :offset [half-w half-h])
        at (fn [c wx wy] (cam/world->screen (assoc c :rotation 0.0 :zoom base-zoom) [wx wy]))
        [tx ty] (:target cam)]
    (case mode
      :centre (assoc cam :target [x y])

      :clamped
      (let [c (assoc cam :target [x y])
            xs (mapcat (fn [[ex ey ew eh]] [[ex ey] [(+ ex ew) (+ ey eh)]]) env-items)
            [min-x min-y] [(apply min (map first xs)) (apply min (map second xs))]
            [max-x max-y] [(apply max (map first xs)) (apply max (map second xs))]
            [sx-max sy-max] (at c max-x max-y)
            [sx-min sy-min] (at c min-x min-y)
            [ox oy] (:offset c)
            ox (if (< sx-max vw) (- vw (- sx-max half-w)) ox)
            oy (if (< sy-max vh) (- vh (- sy-max half-h)) oy)
            ox (if (> sx-min 0) (- half-w sx-min) ox)
            oy (if (> sy-min 0) (- half-h sy-min) oy)]
        (assoc c :offset [ox oy]))

      :smooth
      (let [dx (- x tx)
            dy (- y ty)
            len (Math/sqrt (+ (* dx dx) (* dy dy)))]
        (if (> len 10.0)
          (let [k (/ (* (max 30.0 (* 0.8 len)) dt) len)]
            (assoc cam :target [(+ tx (* dx k)) (+ ty (* dy k))]))
          cam))

      :even-out
      (let [dy (- y ty)]
        (assoc cam :target [x (if (> (abs dy) 1.0)
                                (+ ty (* dy (min 1.0 (* 4.0 dt))))
                                y)]))

      :push
      (let [[sx sy] (at cam x y)
            left (* vw 0.2)
            right (* vw 0.8)
            top (* vh 0.2)
            bottom (* vh 0.8)]
        (assoc cam :target [(cond-> tx
                              (< sx left) (+ (/ (- sx left) base-zoom))
                              (> sx right) (+ (/ (- sx right) base-zoom)))
                            (cond-> ty
                              (< sy top) (+ (/ (- sy top) base-zoom))
                              (> sy bottom) (+ (/ (- sy bottom) base-zoom)))]))

      cam)))

(defn camera
  "The `net.b12n.raylib-ios.camera2d` camera for `state` in `dims`: the offset moved from
  the field's pixels to scene pixels, the base zoom, no rotation."
  [state dims]
  (let [[fx fy] (:field dims)
        [ox oy] (:offset state)]
    {:offset [(+ fx ox) (+ fy oy)]
     :target (:target state)
     :rotation 0.0
     :zoom (:base-zoom dims)}))

(defn advance
  "One frame. The player walks and jumps from the held buttons for
  `:delta-seconds` (clamped at 0 below, not above), the camera follows, and a
  press of camera or reset acts once. Reset restores the player and the camera
  and ignores the mode's follow that frame, as the original does."
  [state input]
  (let [dims (geometry (:metrics input))
        held (held-buttons dims input)
        pressed (fn [id] (and (contains? held id) (not (contains? (:held state) id))))
        dt (max 0.0 (double (or (:delta-seconds input) 0.0)))
        mode (if (pressed :camera) (mod (inc (:mode state)) (count modes)) (:mode state))
        [_ _ vw vh] (:field dims)
        half [(/ vw 2.0) (/ vh 2.0)]]
    (if (pressed :reset)
      (assoc state
             :player start-player
             :target start-target
             :offset half
             :mode mode
             :held held)
      (let [player (update-player (:player state) held dt)
            c (update-camera (mode-id mode)
                             {:offset half
                              :target (:target state)}
                             player dt dims)]
        (assoc state
               :player player
               :target (:target c)
               :offset (:offset c)
               :mode mode
               :held held)))))

(defn- init [{:keys [metrics]}]
  (let [[_ _ vw vh] (:field (geometry metrics))]
    [{:player start-player
      :target start-target
      :offset [(/ vw 2.0) (/ vh 2.0)]
      :mode 0
      :held #{}}
     [[:scene/init :platformer]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :platformer]]])

(defn scene []
  {:id :platformer
   :title "2D Platformer"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
