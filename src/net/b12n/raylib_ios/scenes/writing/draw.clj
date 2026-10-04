(ns net.b12n.raylib-ios.scenes.writing.draw
  "The draw-scene! method for the `:writing` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.writing :as writ]))

(defmethod draw-scene! :writing [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (writ/dimensions m)
        lines (writ/wrap (writ/visible t) (:columns d))
        ink (rl/rgba 0 82 172 255)]
    (doseq [[i line] (map-indexed vector lines)]
      (rl/draw-text line (int (:margin d)) (int (+ (:top d) (* i (:line-height d))))
                    (:size d) ink))
    ;; a cursor while typing, gone during the pause, which is how you can tell
    ;; the difference between finished and stalled
    (when-not (writ/complete? t)
      (let [row (max 0 (dec (count lines)))
            last-line (if (seq lines) (last lines) "")]
        (rl/draw-rectangle
         (int (+ (:margin d) (rl/measure-text last-line (:size d)) (* 0.2 (:size d))))
         (int (+ (:top d) (* row (:line-height d))))
         (int (* 0.09 (:size d))) (:size d) ink)))))
