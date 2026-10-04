(ns net.b12n.raylib-ios.scenes.boids.draw
  "The draw-scene! method for the `:boids` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.boids :as boids]))

(defmethod draw-scene! :boids [_ {:keys [flock]} {:keys [m]}]
  (let [dims (boids/dimensions m)
        body (:body dims)
        wing (rl/rgba 120 200 255 255)]
    (rl/clear-background (rl/rgba 20 20 30 255))
    (doseq [b flock]
      (let [[hx hy] (boids/heading b dims)]
        (rl/draw-line (int (:x b)) (int (:y b)) (int hx) (int hy) wing)
        (rl/draw-circle (int (:x b)) (int (:y b)) (double body) rl/SKYBLUE)))))
