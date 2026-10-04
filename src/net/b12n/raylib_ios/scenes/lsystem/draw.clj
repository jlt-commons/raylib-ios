(ns net.b12n.raylib-ios.scenes.lsystem.draw
  "The draw-scene! method for the `:lsystem` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.lsystem :as lsys]))

(defmethod draw-scene! :lsystem [_ {:keys [segments frame]} _]
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [n (lsys/shown frame (count segments))
        green (rl/rgba 0 228 48 255)]
    (loop [i 0]
      (when (< i n)
        (let [s (nth segments i)]
          (rl/draw-line (int (nth s 0)) (int (nth s 1)) (int (nth s 2)) (int (nth s 3)) green))
        (recur (inc i))))))
