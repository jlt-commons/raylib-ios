(ns net.b12n.raylib-ios.scenes.dashed.draw
  "The draw-scene! method for the `:dashed` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene! stroke!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.dashed :as dash]))

(defmethod draw-scene! :dashed [_ {:keys [target]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (dash/dimensions m)
        maroon (rl/rgba 190 33 55 255)]
    (doseq [seg (dash/dashes d target)]
      (stroke! (int (nth seg 0)) (int (nth seg 1))
               (int (nth seg 2)) (int (nth seg 3)) maroon))
    (rl/draw-circle (int (:cx d)) (int (:cy d)) (:hub d) (rl/rgba 80 80 80 255))
    (rl/draw-circle (int (first target)) (int (second target)) (* 0.6 (:hub d)) maroon)))
