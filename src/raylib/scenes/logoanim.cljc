(ns raylib.scenes.logoanim
  "raylib's logo assembling itself, ported from raylib-jlt's `logo_anim`, which
  ports raylib's own shapes_logo_raylib_anim.c.

  A square blinks, the top and left bars grow out of it, the bottom and right
  close the frame, the letters arrive one at a time, and the whole thing fades.
  Then it starts again, which is the one change from the original: that has R to
  replay and this has no keyboard, so it loops.

  Nothing here is eased, and that is deliberate rather than lazy. Every stage
  advances by a fixed step per frame and changes when a counter hits an exact
  value, which is why it feels mechanical. The original reads that way and it is
  worth keeping, since the point of the piece is the assembly and not the
  motion.

  The logo is 256 units square with a 16-unit border in raylib's own source. Both
  are scaled to the screen here, so the proportions survive on a phone."
  (:require [clojure.string]))

(def logo-units 256)
(def border-units 16)
(def blink-frames 120)
(def bar-step-units 4)
(def frames-per-letter 12)
(def fade-step 0.02)
(def word "raylib")

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (* 0.72 (min w h))
        scale (/ side logo-units)]
    {:w w :h h
     :side side
     :scale scale
     :border (* border-units scale)
     :x (- (* 0.5 w) (* 0.5 side))
     :y (- (* 0.5 h) (* 0.5 side))}))

(defn initial-state []
  {:stage 0 :counter 0
   :top border-units :left border-units
   :bottom border-units :right border-units
   :letters 0 :alpha 1.0})

(defn advance
  "One frame of the state machine. Stages, in order: blink, grow the top and
  left bars, close the bottom and right, spell the word and fade, then restart.

  Units rather than pixels throughout, so the machine is the same on any screen
  and only the drawing scales."
  [{:keys [stage counter top left bottom right letters alpha] :as s}]
  (case stage
    0 (if (>= counter blink-frames)
        (assoc s :stage 1 :counter 0)
        (update s :counter inc))

    1 (let [t (+ top bar-step-units)
            l (+ left bar-step-units)]
        (assoc s :top t :left l :stage (if (>= t logo-units) 2 1)))

    2 (let [b (+ bottom bar-step-units)
            r (+ right bar-step-units)]
        (assoc s :bottom b :right r :stage (if (>= b logo-units) 3 2)))

    3 (let [c (inc counter)
            [c letters] (if (>= c frames-per-letter) [0 (inc letters)] [c letters])
            a (if (>= letters (count word)) (- alpha fade-step) alpha)]
        (if (<= a 0.0)
          (initial-state)
          (assoc s :counter c :letters letters :alpha a)))

    s))

(defn blink-on?
  "The opening square is lit for half of each thirty-frame cycle."
  [counter]
  (zero? (mod (quot counter 15) 2)))

(defn visible-word [letters]
  (subs word 0 (min (count word) letters)))

(defn- init [_] [(initial-state) [[:scene/init :logoanim]]])
(defn- update-scene [state _] [(advance state) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :logoanim]]])

(defn scene []
  {:id :logoanim :title "raylib Logo"
   :init init :update update-scene :draw draw :dispose dispose})
