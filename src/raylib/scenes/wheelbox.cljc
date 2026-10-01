(ns raylib.scenes.wheelbox
  "A box dragged up and down. Ported from raylib-jlt's `wheel`, which is
  raylib's `core_input_mouse_wheel` example.

  The original moves a MAROON square of side 80, centred across an 800x450
  window, by 20 pixels a wheel notch, held inside the window, with a DARKGRAY
  caption at (10, 10). A phone has no wheel, so a vertical DRAG moves the box
  instead. The scene keeps its own anchor, because the gesture layer only reports
  a swipe at the release. On `:press` below Back it records the finger's y and
  the box's y. While the finger is down the box is at the anchored y plus the
  distance the finger has moved since, so the box follows the finger one for one
  and a drag back to where it started returns the box. The distance is read in
  screen pixels, which are already the scaled units, so the original's 20 pixel
  notch becomes whatever the finger travels. Only the raw `:pointer` on `:press`
  and `:down` is read, never the `:release`. The same finger reports a `:swipe`
  on release, which nothing here reads, so the drag is never applied a second
  time. A press under `gesture/back-region` belongs to the host and anchors
  nothing.

  The play field is the full width, from the bottom of Back to the bottom of the
  screen, and the box is held wholly inside it, as the original holds it inside
  the window. The clamp runs every frame, so a rotation pulls the box back in.
  The box is the original's 80 scaled by one factor `:u`, the geometric mean of
  the two axes' scales, and sits in the middle of the width. The caption uses
  the smaller scale so it fits whichever way the phone is held, and is drawn
  after the box, as in the original, so it stays legible when the box is at the
  top. Nothing moves by itself, so there is no timing. Colours are `[r g b a]`
  vectors. The draw method packs them with `rl/rgba`."
  (:require [raylib.gesture :as gesture]))

(def box-side "The box's side at scale 1. The original's." 80.0)

(def background-colour [245 245 245 255])
(def box-colour [190 33 55 255])
(def caption-colour [80 80 80 255])

(def caption "drag to move the box up and down")

(defn dimensions
  "The layout for `metrics`' `:screen`. The play field is `:fx :ftop :fw :fh`:
  the full width, from the bottom of `gesture/back-region` to the bottom. `:side`
  is the box's side (see the namespace docstring) and `:box-x` its left edge,
  which never changes. `:min-y` and `:max-y` bound the box's top. `:caption` is
  `{:s :x :y :size}`, left-aligned below Back, so nothing is measured here."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        fh (- h top)
        sx (/ w 800.0)
        sy (/ fh 450.0)
        u (Math/sqrt (* sx sy))
        ts (min sx sy)
        pad (max 8 (int (* 10 ts)))
        side (* box-side u)]
    {:w w
     :h h
     :fx 0.0
     :ftop (double top)
     :fw (double w)
     :fh (double fh)
     :u u
     :side side
     :box-x (/ (- w side) 2.0)
     :min-y (double top)
     :max-y (double (- h side))
     :caption {:s caption
               :x pad
               :y (+ top pad)
               :size (max 20 (int (* 20 ts)))}}))

(defn clamp-y
  "The box's top `y` held inside the play field of `dims`."
  [{:keys [min-y max-y]} y]
  (max min-y (min max-y (double y))))

(defn- anchor
  "The drag anchor for this frame: a fresh one on a `:press` outside Back, the
  existing one while the finger stays down, and none otherwise."
  [{:keys [drag y]} {:keys [phase position]}]
  (case phase
    :press (when (and position (not (gesture/in-back-region? position)))
             {:finger-y (double (second position))
              :box-y y})
    :down drag
    nil))

(defn advance
  "One frame. While a finger is down and anchored the box is at the anchored y
  plus the finger's travel since, otherwise it stays, and either way it is held
  inside the play field, which also pulls it back in after a rotation. A swipe
  is never read, and neither is the release position."
  [state input]
  (let [dims (dimensions (:metrics input))
        drag (anchor state (:pointer input))
        y (if (and drag (gesture/down? input))
            (+ (:box-y drag)
               (- (double (second (get-in input [:pointer :position])))
                  (:finger-y drag)))
            (:y state))]
    (assoc state
           :drag drag
           :y (clamp-y dims y))))

(defn- init [{:keys [metrics]}]
  (let [{:keys [ftop fh side]} (dimensions metrics)]
    [{:y (+ ftop (/ (- fh side) 2.0))
      :drag nil}
     [[:scene/init :wheelbox]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :wheelbox]]])

(defn scene []
  {:id :wheelbox
   :title "Mouse Wheel"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
