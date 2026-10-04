(ns net.b12n.raylib-ios.scenes.epicycles.draw
  "The draw-scene! method for the `:epicycles` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.epicycles :as epi]))

(defmethod draw-scene! :epicycles [_ {:keys [theta trace]} {:keys [m]}]
  (let [dims (epi/dimensions m)
        {:keys [trace-top trace-step]} dims
        {:keys [centers radii]} (epi/chain dims theta)
        rn (count radii)
        faint (rl/rgba 70 70 80 255)
        rod (rl/rgba 130 130 130 255)
        wave (rl/rgba 255 203 0 255)]
    (rl/clear-background (rl/rgba 0 0 0 255))
    (loop [i 0]
      (when (< i rn)
        (let [a (nth centers i)
              b (nth centers (inc i))]
          (rl/draw-circle-lines (int (nth a 0)) (int (nth a 1)) (double (nth radii i)) faint)
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1)) rod))
        (recur (inc i))))
    ;; the pen, and the line joining it to where the wave starts
    (let [pen (nth centers rn)
          px (int (nth pen 0))
          py (int (nth pen 1))]
      (rl/draw-line px py px (int trace-top) (rl/rgba 90 90 90 255))
      (let [n (count trace)]
        (loop [i 1]
          (when (< i n)
            (rl/draw-line (int (nth trace (dec i))) (int (+ trace-top (* (dec i) trace-step)))
                          (int (nth trace i)) (int (+ trace-top (* i trace-step)))
                          wave)
            (recur (inc i))))))))
