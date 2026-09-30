(ns raylib.scenes.deltatime
  "Two boxes cross the screen, one by a fixed step per frame and one by a
  distance per second. Ported from raylib-jlt's `delta_time`.

  The phone holds a steady 60 fps, so the two boxes move together until the
  frame rate drops. Show it live from the nREPL with
  `(raylib.host/on-next-frame! (fn [] (raylib.host/set-target-fps 30)))`: the
  per-frame box slows to half speed while the delta-time box does not."
  (:require [clojure.string]))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        box (* 0.05 side)]
    {:box box
     :top-y (- (* 0.35 h) (* 0.5 box))
     :bottom-y (- (* 0.65 h) (* 0.5 box))
     :label-size (max 20 (int (* 0.034 side)))
     :w w}))

(defn advance
  "Move both boxes one frame. The per-frame box gains a fixed `w / 200` pixels.
  The delta-time box gains `60 * w / 200 * dt`, which is the same distance at
  exactly 60 fps and proportionally more or less at any other rate.

  `dt` is clamped at 0 the way `raylib.scenes.analog/advance` clamps it, so a
  bad frame clock cannot drive a box backwards."
  [state input]
  (let [w (double (first (:screen (:metrics input))))
        dt (max 0.0 (double (:delta-seconds input 0.0)))
        step (/ w 200.0)]
    (assoc state
           :xf (mod (+ (:xf state) step) w)
           :xd (mod (+ (:xd state) (* 60.0 step dt)) w))))

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
