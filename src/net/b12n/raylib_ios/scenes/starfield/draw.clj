(ns net.b12n.raylib-ios.scenes.starfield.draw
  "The draw-scene! method for the `:starfield` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.starfield :as sfield]))

(defmethod draw-scene! :starfield [_ {:keys [stars streaks? speed]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack sfield/background-colour))
        dims (sfield/dimensions m)
        white (pack sfield/star-colour)
        thick (:u dims)]
    (doseq [s stars
            :let [{:keys [x y r tx ty]} (sfield/star-shape dims s)]]
      (if streaks?
        (rl/draw-line-ex tx ty x y thick white)
        (rl/draw-circle (int x) (int y) (float r) white)))
    (let [[speed-l mode-l fps-l] (:lines dims)]
      (rl/draw-text (sfield/speed-line speed) (:x speed-l) (:y speed-l) (:size speed-l) white)
      (rl/draw-text (sfield/mode-line streaks?) (:x mode-l) (:y mode-l) (:size mode-l) white)
      (rl/draw-text (sfield/fps-line (rl/get-fps)) (:x fps-l) (:y fps-l) (:size fps-l) white))))
