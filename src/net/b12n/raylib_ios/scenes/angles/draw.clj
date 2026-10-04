(ns net.b12n.raylib-ios.scenes.angles.draw
  "The draw-scene! method for the `:angles` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene! stroke!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.angles :as ang]))

(defmethod draw-scene! :angles [_ {:keys [angle]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (ang/dimensions m)
        cx (int (:cx d)) cy (int (:cy d))
        grey (rl/rgba 200 200 200 255)
        maroon (rl/rgba 190 33 55 255)]
    (rl/draw-circle-lines cx cy (:radius d) grey)
    (doseq [a (ang/fixed-angles)]
      (let [[x y] (ang/spoke-end d a)]
        (stroke! cx cy (int x) (int y) grey)))
    (let [[x y] (ang/spoke-end d angle)]
      (stroke! cx cy (int x) (int y) maroon)
      (rl/draw-circle (int x) (int y) (* 0.03 (:radius d)) maroon))))
