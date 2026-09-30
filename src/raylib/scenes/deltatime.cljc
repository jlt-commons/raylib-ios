(ns raylib.scenes.deltatime
  "Two boxes cross the screen, one by a fixed step per frame and one by a
  distance scaled by the time since the last frame. Ported from raylib-jlt's
  `delta_time`.

  The two agree only at exactly 60 fps, and a phone does not quite get there.
  Measured on an iPhone 17 Pro, the mean frame is 17.1 ms, about 58.5 fps, so
  the delta-time box gains roughly 9 pixels a second and the pair drift apart
  with nothing else going on. Drop the rate from the nREPL with
  `(raylib.host/on-next-frame! (fn [] (raylib.host/set-target-fps 30)))` and the
  per-frame box falls to half speed while the delta-time box keeps its pace.")

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        box (* 0.05 side)
        label-size (max 20 (int (* 0.034 side)))]
    {:box box
     :top-y (- (* 0.35 h) (* 0.5 box))
     :bottom-y (- (* 0.65 h) (* 0.5 box))
     :label-size label-size
     :w w
     ;; Both boxes wrap here rather than at `w`, so a box never hangs off the
     ;; right edge of the safe region.
     :wrap-at (- w box)
     :label-x (int (* 0.04 w))
     :top-label-y (int (- (* 0.35 h) (* 0.5 box) label-size 8))
     :bottom-label-y (int (- (* 0.65 h) (* 0.5 box) label-size 8))
     :fps-y (int (- h (* 0.055 h)))}))

(defn advance
  "Move both boxes one frame. The per-frame box gains a fixed `w / 200` pixels.
  The delta-time box gains `60 * w / 200 * dt`, which is the same distance at
  exactly 60 fps and proportionally more or less at any other rate.

  `dt` is clamped at 0 the way `raylib.scenes.analog/advance` clamps it, so a
  bad frame clock cannot drive a box backwards. Both wrap at `w - box` so the
  whole box stays on screen."
  [state input]
  (let [{:keys [wrap-at w]} (dimensions (:metrics input))
        w (double w)
        wrap-at (double wrap-at)
        dt (max 0.0 (double (:delta-seconds input 0.0)))
        step (/ w 200.0)]
    (assoc state
           :xf (mod (+ (:xf state) step) wrap-at)
           :xd (mod (+ (:xd state) (* 60.0 step dt)) wrap-at))))

(defn- init [_] [{:xf 0.0
                  :xd 0.0} [[:scene/init :deltatime]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :deltatime]]])

(defn scene []
  {:id :deltatime
   :title "Delta Time"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
