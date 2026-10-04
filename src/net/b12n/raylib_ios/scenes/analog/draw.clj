(ns net.b12n.raylib-ios.scenes.analog.draw
  "The draw-scene! method for the `:analog` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.analog :as analog]))

(defmethod draw-scene! :analog [_ {:keys [frac]} {:keys [m]}]
  (rl/clear-background (rl/rgba 24 26 33 255))
  ;; Read here, not in the scene, for the reason the digital clock gives above.
  (let [now (rl/local-time)
        [h mi s] now
        {:keys [cx cy r label-size]
         :as d} (analog/dimensions m)
        {:keys [hour minute second]} (analog/hand-angles now frac)
        pale (rl/rgba 235 235 245 255)
        red (rl/rgba 235 90 90 255)
        hand (fn [ang len thick colour]
               (let [[x y] (analog/polar cx cy len ang)]
                 (rl/draw-line-ex cx cy x y thick colour)))]
    (rl/draw-ring (int cx) (int cy) (- r (* r 0.05)) r 0 360 120 (rl/rgba 210 212 222 255))
    (doseq [[x0 y0 x1 y1 long?] (analog/ticks d)]
      (rl/draw-line-ex x0 y0 x1 y1 (if long? (* r 0.017) (* r 0.006))
                       (rl/rgba 150 155 165 255)))
    (hand hour   (* r 0.50) (* r 0.045) pale)
    (hand minute (* r 0.72) (* r 0.028) pale)
    (hand second (* r 0.84) (* r 0.012) red)
    (rl/draw-circle (int cx) (int cy) (float (* r 0.045)) red)
    ;; Bottom left, same reason the automaton's rule label is there: the host
    ;; owns the top left for Back, and the first version of this sat under it.
    (let [[sw sh] (:screen m)]
      (rl/draw-text (str (when (< h 10) "0") h ":"
                         (when (< mi 10) "0") mi ":"
                         (when (< s 10) "0") s)
                    (int (* 0.04 sw)) (int (- sh (* 0.055 sh)))
                    label-size rl/RAYWHITE))))
