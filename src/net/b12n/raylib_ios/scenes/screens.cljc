(ns net.b12n.raylib-ios.scenes.screens
  "A screen manager: LOGO, TITLE, GAMEPLAY, ENDING, each a flat colour and a
  label. Ported from raylib-jlt's `basic_screen_manager`, where ENTER steps to
  the next screen and a timer steps on its own. It differs from the other
  scenes in having nothing to draw but a state machine, so what it shows is the
  transition rule.

  A tap stands in for ENTER, and the timer is the original's: frame-locked, one
  step on every 90th frame counted from the start, about 1.5 s at 60 fps. The
  counter belongs to the scene and is never reset, so a tap does not postpone
  the next timed step, which is how the original behaves. ENDING steps back to
  LOGO, because the original takes `(mod (inc idx) 4)`.

  A tap and the timer on the same frame step once, because both only decide
  whether to step and the step itself happens once. When the timer fires with a
  touch already in progress, the touch is dropped by storing `gesture/idle`:
  that finger landed on a screen that has gone, and its lift would otherwise
  skip the new one before anyone had seen it. A press on the timer frame itself
  is kept, since it starts on the new screen and is an ordinary tap. A tap is judged at where the finger started, so one that
  starts under Back belongs to the host and steps nothing.

  Text is laid out in `dimensions`: the label and the hint are left-aligned, so
  no text is measured. Both are scaled by one factor from the original's 800 by
  450 window and placed below `gesture/back-region`."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def order "The original's screens, in the order it steps through them."
  [:logo :title :gameplay :ending])

(def timer-frames "The original's auto-advance, in frames." 90)

(def ^:private backgrounds
  {:logo [245 245 245 255] ; RAYWHITE
   :title [0 82 172 255] ; DARKBLUE
   :gameplay [0 117 44 255] ; DARKGREEN
   :ending [190 33 55 255]}) ; MAROON

(def ^:private labels
  {:logo "LOGO"
   :title "TITLE SCREEN"
   :gameplay "GAMEPLAY"
   :ending "ENDING"})

(def dark-text "The original's DARKGRAY, used on the LOGO screen." [80 80 80 255])
(def light-text "The original's RAYWHITE, used on every other screen." [245 245 245 255])

(defn background
  "The colour `[r g b a]` of screen `s`."
  [s]
  (get backgrounds s))

(defn lines
  "The two text rows of screen `s` as `[string colour]`: the label, then the
  hint that replaces the original's \"press ENTER to advance\"."
  [s]
  (let [c (if (= s :logo) dark-text light-text)]
    [[(get labels s) c]
     ["tap to advance" c]]))

(defn next-screen
  "The screen after `s`, wrapping from ENDING to LOGO."
  [s]
  (get (zipmap order (rest (cycle order))) s))

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h` and `:rows`, the label and the
  hint as `{:x :y :size}`. One factor `u` scales the original's 800 by 450
  layout, the smaller of the width over 800 and the room below Back over 450,
  with the label at 40 and the hint at 20 in the original. Both rows start below
  `gesture/back-region`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        u (min (/ w 800.0) (/ (- h top) 450.0))
        label (max 12 (int (* 40 u)))
        hint (max 8 (int (* 20 u)))
        x (int (* 60 u))
        y (int (+ top (* 0.05 h)))]
    {:w w
     :h h
     :rows [{:x x
             :y y
             :size label}
            {:x x
             :y (int (+ y label (* 0.5 label)))
             :size hint}]}))

(defn advance
  "One frame. The frame counter always counts. A tap that starts outside Back or
  the timer steps to the next screen, once. A timer step also drops a touch already
  in progress, as the namespace docstring explains."
  [state input]
  (let [[g' event] (gesture/track (:g state) input)
        frame (inc (:frame state))
        timer? (zero? (mod frame timer-frames))
        tap? (and (= :tap (:type event))
                  (not (gesture/in-back-region? (:at event))))]
    {:g (if (and timer? (not= :press (get-in input [:pointer :phase])))
          gesture/idle
          g')
     :frame frame
     :screen (if (or tap? timer?)
               (next-screen (:screen state))
               (:screen state))}))

(defn- init [_]
  [{:g gesture/idle
    :frame 0
    :screen :logo}
   [[:scene/init :screens]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :screens]]])

(defn scene []
  {:id :screens
   :title "Screen Manager"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
