(ns net.b12n.raylib-ios.scenes.automata.draw
  "The draw-scene! method for the `:automata` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.automata :as auto]))

(defmethod draw-scene! :automata [_ {:keys [window]
                                     :as state} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [px row-h]} (auto/dimensions m)
        ink (rl/rgba 20 30 60 255)
        rows (count window)]
    ;; Runs, not cells: each [start length] is one rectangle covering however
    ;; many adjacent live cells it found. See the namespace docstring.
    (loop [r 0]
      (when (< r rows)
        (let [y (* r row-h)
              rr (nth window r)
              n (count rr)]
          (loop [i 0]
            (when (< i n)
              (let [run (nth rr i)]
                (rl/draw-rectangle (* (nth run 0) px) y (* (nth run 1) px) row-h ink))
              (recur (inc i)))))
        (recur (inc r))))
    ;; Bottom left, because the top left is where the host puts the Back button
    ;; and the first version of this label sat underneath it.
    (let [[sw sh] (:screen m)]
      (rl/draw-text (str "rule " (auto/rule-of state))
                    (int (* 0.04 sw)) (int (- sh (* 0.055 sh)))
                    ;; dark ink: the background here is RAYWHITE, and the
                    ;; first version of this label was near-white on it
                    30 (rl/rgba 80 80 80 255)))))
