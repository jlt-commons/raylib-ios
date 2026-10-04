(ns net.b12n.raylib-ios.scenes.pendulum.draw
  "The draw-scene! method for the `:pendulum` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.pendulum :as pend]))

(defmethod draw-scene! :pendulum [_ {:keys [trail]
                                     :as st} {:keys [m]}]
  ;; Indexed loops throughout, per docs/guide/performance-on-a-phone.md.
  (let [{:keys [ox oy bob trail-dot]} (pend/dimensions m)
        [[x1 y1] [x2 y2]] (pend/positions st m)
        n (count trail)]
    (rl/clear-background (rl/rgba 20 20 30 255))
    (loop [i 0]
      (when (< i n)
        (let [p (nth trail i)
              t (/ (double (inc i)) n)]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (* 2.0 trail-dot t)
                          (rl/rgba 80 200 255 (int (* 200 t)))))
        (recur (inc i))))
    (rl/draw-line (int ox) (int oy) (int x1) (int y1) rl/RAYWHITE)
    (rl/draw-line (int x1) (int y1) (int x2) (int y2) rl/RAYWHITE)
    (rl/draw-circle (int x1) (int y1) (double bob) (rl/rgba 255 203 0 255))
    (rl/draw-circle (int x2) (int y2) (double bob) rl/MAROON)))
