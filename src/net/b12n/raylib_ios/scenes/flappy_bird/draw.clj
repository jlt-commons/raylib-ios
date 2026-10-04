(ns net.b12n.raylib-ios.scenes.flappy-bird.draw
  "The draw-scene! method for the `:flappy-bird` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.flappy :as flappy-draw]
            [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]))

(defmethod draw-scene! :flappy-bird [_ game {:keys [k m]}]
  (flappy-draw/draw-game! k game m))
