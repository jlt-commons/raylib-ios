(ns net.b12n.raylib-ios.scenes.screenbuf.draw
  "The draw-scene! method for the `:screenbuf` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!]]
            [net.b12n.raylib-ios.scenes.screenbuf :as screenbuf]
            [net.b12n.raylib-ios.texture :as texture]))

(defmethod draw-scene! :screenbuf [_ state {:keys [m]}]
  (clear-to! screenbuf/background-colour)
  (let [[y0 rows] (screenbuf/upload-rows (:stepped state))
        id (texture/band! :screenbuf :fire (screenbuf/spec (:buf state)) y0 rows)]
    (texture/quad! id (screenbuf/geometry m))))
