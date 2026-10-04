(ns net.b12n.raylib-ios.scenes.sequence.draw
  "The draw-scene! method for the `:sequence` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.sequence :as seqn]))

(defmethod draw-scene! :sequence [_ {:keys [bars]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (seqn/dimensions m)
        bw (:bar-w d)]
    (doseq [[i bar] (map-indexed vector bars)]
      (let [[r g b] (:colour bar)
            height (* (:fraction bar) (:max-height d))]
        (rl/draw-rectangle (int (* i bw))
                           (int (- (:baseline d) height))
                           (int (- bw 2))
                           (int height)
                           (rl/rgba r g b 255))))))
