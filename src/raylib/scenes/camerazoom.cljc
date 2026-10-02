(ns raylib.scenes.camerazoom
  "A 2D camera that zooms about the point you pin. Ported from raylib-jlt's
  `camera-2d-mouse-zoom`, which is raylib's `core_2d_camera_mouse_zoom` example
  (zlib licence).

  The original draws a world of a 21x21 grid of 50-unit cells, a red square at
  the origin, a blue circle, a lime box and the text \"world origin\" through a
  Camera2D, with a crosshair at the cursor and two lines of text along the
  bottom. A left-drag pans by the mouse delta over the zoom. Key 1 zooms on the
  wheel and key 2 zooms by dragging with the right button, both after pinning
  the cursor, and both in log space, `exp(log(zoom) + k)`, clamped to 0.125 to
  64. The grid, the shapes, the colours, the crosshair and the clamp are the
  original's.

  Controls here:
  - A one-finger drag pans. Each `:down` frame moves the target by minus the
    finger's movement since the last frame, divided by the zoom, so the world
    point under the finger stays under it. The press frame only anchors the
    drag, and the release position is never read.
  - A two-finger pinch zooms, in place of the wheel and the right-drag. Modes 1
    and 2 are dropped: there is one zoom, so there is no mode to pick, and the
    HUD no longer names one. Each frame the camera is pinned (see
    `raylib.camera2d/pin`) on the world point that was under the pinch's
    previous midpoint, its offset is moved to the midpoint now, and then the
    zoom becomes `exp(log(zoom) + log(ratio))`, where `ratio` is the change of
    finger distance (`raylib.camera2d/pinch-step`). Held midpoint, that pins the
    point under it at any zoom, as the original does under the cursor. A midpoint
    that drifts drags the world with it, which the original's wheel could not.
    The twist of the fingers is ignored.

  A pinch and a pan never share a finger. A second finger landing ends the pan
  that frame, and with two or more fingers down only the pinch acts. A pinch
  acts only while the finger count stays at two (`raylib.camera2d/pinch-frame`):
  a third finger landing, one of three lifting or a platform reorder of three
  points only records the new fingers and moves nothing. When one finger lifts,
  the one left pans nothing until a fresh press, and the next pair of fingers
  starts a pinch from its first frame with no jump. A touch that begins under Back pans nothing.

  The crosshair sits at the last touch, or the pinch's midpoint. The camera
  starts with the world origin at the field's centre, where the original starts
  it at the window's top-left, because a phone's top-left is under Back. The
  field is everything below Back and the text lines are drawn over it along the
  bottom, as in the original. Zoom is printed with three decimals by
  `zoom-label`, which needs no `format`. Colours are `[r g b a]` vectors. Nothing
  here reads a frame time, like the original."
  (:require [raylib.camera2d :as cam]
            [raylib.gesture :as gesture]))

(def zoom-min "Smallest zoom. The original's." 0.125)
(def zoom-max "Largest zoom. The original's." 64.0)
(def cells "Grid cells along each side. The original's 21." 21)
(def cell "A grid cell's side in world units. The original's." 50)

(def background-colour [245 245 245 255])
(def grid-colour [220 220 220 255])
(def square-colour [230 41 55 255])
(def circle-colour [0 121 241 255])
(def box-colour [0 158 47 255])
(def world-text-colour [80 80 80 255])
(def cross-colour [80 80 80 255])
(def hint-colour [80 80 80 255])
(def zoom-colour [190 33 55 255])

(def hint "drag: pan - pinch: zoom")

(defn zoom-label
  "\"zoom \" and `z` to three decimals, rounded half up, without `format`."
  [z]
  (let [n (long (Math/floor (+ 0.5 (* 1000.0 z))))
        frac (rem n 1000)]
    (str "zoom " (quot n 1000) "."
         (when (< frac 100) "0")
         (when (< frac 10) "0")
         frac)))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure. `:field` is
  `[x y w h]`: the full width from below Back to the bottom, which the world is
  clipped to. `:offset` is its centre. `:size` is the text size, the larger of
  16 and 0.03 of the shorter side, and `:hint-y` and `:zoom-y` are the two text
  lines along the bottom."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        zoom-y (- h pad size)
        hint-y (- zoom-y size (quot pad 2))
        fh (- h top)]
    {:w w
     :h h
     :size size
     :pad pad
     :hint-y hint-y
     :zoom-y zoom-y
     :field [0.0 (double top) (double w) (double fh)]
     :offset [(* 0.5 w) (+ top (* 0.5 fh))]}))

(defn dimensions
  "`geometry` plus the text: `:hint` and `:zoom-line`, each `{:s :x :y :size}`,
  and `:lines` with both so a test can check they fit. `:zoom-line`'s `:s` is
  the widest the line can be, which is what is measured, so a draw replaces it
  with `zoom-label` and measures nothing. The size is cut back from
  `geometry`'s when the widest line would cover more than 0.92 of the width.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w size pad hint-y zoom-y]
         :as geo} (geometry metrics)
        widest-zoom (zoom-label zoom-max)
        widest (max (measure hint 100) (measure widest-zoom 100))
        size (max 8 (min size (int (/ (* 0.92 w 100.0) widest))))
        lines {:hint {:s hint
                      :x pad
                      :y hint-y
                      :size size}
               :zoom-line {:s widest-zoom
                           :x pad
                           :y zoom-y
                           :size size}}]
    (assoc (merge geo lines)
           :text-size size
           :lines (vec (vals lines)))))

(defn- clamp-zoom [z] (max zoom-min (min zoom-max z)))

(defn- pinch-camera
  "`camera` after the pinch moved from `prev` to `now` (both from
  `raylib.camera2d/pinch`): the world point under the previous midpoint is
  pinned to the midpoint now, and the zoom follows the change of distance in
  log space, held to the clamp."
  [camera prev now]
  (let [{:keys [ratio mid]} (cam/pinch-step prev now)
        pinned (assoc (cam/pin camera (:mid prev)) :offset (vec mid))]
    (assoc pinned
           :zoom (clamp-zoom (Math/exp (+ (Math/log (:zoom pinned))
                                          (Math/log ratio)))))))

(defn- pan-camera
  "`camera` after a finger moved from `from` to `to`: the target moves by minus
  the movement over the zoom."
  [camera [fx fy] [tx ty]]
  (let [z (:zoom camera)
        [gx gy] (:target camera)]
    (assoc camera :target [(- gx (/ (- tx fx) z)) (- gy (/ (- ty fy) z))])))

(defn advance
  "One frame. Two or more touch points drop the pan. Exactly two, after exactly
  two the frame before (`cam/pinch-frame`), pinch: the camera is pinned and
  zoomed by `pinch-camera`. Any change of finger count only records, so a third
  finger, a lifted one or a reordered three moves nothing. With
  fewer than two, a press that is not under Back anchors a pan, a `:down` with
  an anchor pans by the finger's movement since the last frame, and anything
  else, a lifted finger included, ends it. A rotation of the phone drops the pan
  and the pinch, whose pixels are the old screen's, and recentres the camera on
  the new field. The release position is never read."
  [state input]
  (let [metrics (:metrics input)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (-> state
                    (dissoc :drag :pinch)
                    (assoc-in [:camera :offset] (:offset (geometry metrics))))
                state)
        points (vec (:touch-points input))
        pinching? (>= (count points) 2)
        {:keys [phase position]} (:pointer input)
        {now :pinch
         step :step} (cam/pinch-frame (:pinch state) points)
        camera (:camera state)
        anchor (get-in state [:drag :last])
        panning? (and (not pinching?) (= :down phase) anchor position)
        camera (cond
                 step (pinch-camera camera (:pinch state) now)
                 panning? (pan-camera camera anchor position)
                 :else camera)
        drag (case phase
               :press (when (and (not pinching?)
                                 position
                                 (not (gesture/in-back-region? position)))
                        {:last position})
               :down (when (and (not pinching?) anchor position)
                       {:last position})
               nil)
        cross (cond
                now (:mid now)
                (and (gesture/down? input) (not (gesture/in-back-region? position))) position
                :else (:cross state))]
    (assoc state
           :screen screen
           :camera camera
           :pinch now
           :drag drag
           :cross cross)))

(defn- init [{:keys [metrics]}]
  (let [offset (:offset (geometry metrics))]
    [{:camera {:offset offset
               :target [0.0 0.0]
               :rotation 0.0
               :zoom 1.0}
      :cross offset
      :screen (:screen metrics)
      :drag nil
      :pinch nil}
     [[:scene/init :camerazoom]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :camerazoom]]])

(defn scene []
  {:id :camerazoom
   :title "2D Camera Zoom"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
