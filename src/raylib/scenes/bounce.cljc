(ns raylib.scenes.bounce
  "A ball bouncing around the screen; tap to pause. Ported from raylib-jlt's
  `bounce`.

  The original pauses on SPACE. Here a `:tap`, worked out by `raylib.gesture`
  from the whole touch, toggles the pause, so a finger that starts a swipe or
  rests for a long press never pauses it by accident. A tap that lands in the
  Back region is the host's, so it is ignored.

  The original is an 800x450 window with a 20 px ball moving 5 by 4 px a frame.
  Everything scales by `(min w h) / 450`, so the ball keeps its proportions and
  crosses the screen in about the same number of frames on a phone held either
  way up. Like the original this is frame-locked, one step per `advance`.

  The state remembers the `:screen` it was laid out for. When the phone rotates
  the ball is clamped into the new bounds, and keeps its heading and its pause.
  The speed scales with the shorter side, which a rotation leaves unchanged.

  Colours are `[r g b a]` vectors, so the namespace stays pure."
  (:require [raylib.gesture :as gesture]))

(def ball-colour
  "raylib's MAROON."
  [190 33 55 255])

(def hint-colour
  "raylib's LIGHTGRAY is too faint on a phone in daylight, so this is DARKGRAY."
  [80 80 80 255])

(def hint-line "TAP to PAUSE BALL MOVEMENT")
(def paused-line "PAUSED")

(defn dimensions
  "The 800x450 layout scaled by the shorter side. `:speed` is the ball's
  `[vx vy]` per frame. The hint sits along the bottom and PAUSED at the top
  right, clear of the Back button at the top left."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        s (/ side 450.0)]
    {:w w
     :h h
     :scale s
     :radius (* 20 s)
     :speed [(* 5 s) (* 4 s)]
     :hint-size (max 20 (int (* 0.028 side)))
     :hint-x (int (* 0.04 w))
     :hint-y (int (* 0.92 h))
     :paused-size (max 20 (int (* 0.05 side)))
     :paused-x (int (* 0.6 w))
     :paused-y (int (* 0.05 h))}))

(defn- clamp [lo hi n] (max lo (min hi n)))

(defn- move
  "One step of the original's rules. A ball that has crossed a wall is put back
  on it and sent away from it, rather than just having its sign flipped, so a
  ball clamped in by a rotation cannot flip back and forth inside the wall."
  [{:keys [w h radius]} [x y] [vx vy]]
  (let [nx (+ x vx)
        ny (+ y vy)
        hi-x (- w radius)
        hi-y (- h radius)
        [x vx] (cond (<= nx radius) [radius (abs vx)]
                     (>= nx hi-x) [hi-x (- (abs vx))]
                     :else [nx vx])
        [y vy] (cond (<= ny radius) [radius (abs vy)]
                     (>= ny hi-y) [hi-y (- (abs vy))]
                     :else [ny vy])]
    [[x y] [vx vy]]))

(defn- fit
  "`pos` clamped so the whole ball is inside the screen."
  [{:keys [w h radius]} [x y]]
  [(clamp radius (- w radius) x)
   (clamp radius (- h radius) y)])

(defn advance
  "One frame. Calls `gesture/track` once. A tap outside the Back region toggles
  the pause. When the metrics report a different `:screen` the ball is clamped
  into it first, and keeps moving (or stays paused) as it was."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)
        paused? (if (and (= :tap (:type event))
                         (not (gesture/in-back-region? (:at event))))
                  (not (:paused? state))
                  (:paused? state))
        pos (fit dims (:pos state))
        [pos vel] (if paused?
                    [pos (:vel state)]
                    (move dims pos (:vel state)))]
    (assoc state
           :screen [(:w dims) (:h dims)]
           :gesture g
           :paused? paused?
           :pos pos
           :vel vel)))

(defn- init [{:keys [metrics]}]
  (let [dims (dimensions metrics)
        {:keys [w h speed]} dims]
    [{:screen [w h]
      :pos [(* 0.5 w) (* 0.5 h)]
      :vel speed
      :paused? false
      :gesture gesture/idle}
     [[:scene/init :bounce]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :bounce]]])

(defn scene []
  {:id :bounce
   :title "Bouncing Ball"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
