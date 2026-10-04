(ns net.b12n.raylib-ios.scenes.align.draw
  "The draw-scene! method for the `:align` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene! stroke!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.align :as align]))

(defmethod draw-scene! :align [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [box-x box-w box-y box-h gap text-size label-size h]} (align/dimensions m)
        {:keys [word]} (align/current t)
        tw (rl/measure-text word text-size)]
    ;; All three boxes at once, so the comparison does not depend on memory.
    (doseq [[i a] (map-indexed vector align/alignments)]
      (let [by (+ box-y (* i (+ box-h gap)))
            ox (align/offset a box-w tw)]
        (rl/draw-rectangle (int box-x) (int by) (int box-w) (int box-h) (rl/rgba 225 228 236 255))
        (stroke! (int box-x) (int by) (int (+ box-x box-w)) (int by) (rl/rgba 180 184 196 255))
        (stroke! (int box-x) (int (+ by box-h)) (int (+ box-x box-w)) (int (+ by box-h))
                 (rl/rgba 180 184 196 255))
        (rl/draw-text word (int (+ box-x ox))
                      (int (+ by (* 0.5 (- box-h text-size))))
                      text-size (rl/rgba 30 30 40 255))
        (rl/draw-text (name a) (int box-x) (int (- by label-size 6))
                      label-size (rl/rgba 130 130 140 255))))
    (rl/draw-text (str "MeasureText: " tw " px")
                  (int box-x) (int (- h (* 0.16 h))) label-size (rl/rgba 60 60 60 255))))
