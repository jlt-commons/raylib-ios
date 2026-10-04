(ns net.b12n.raylib-ios.scenes.spincubes.draw
  "The draw-scene! method for the `:spincubes` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.spincubes :as spincubes]))

(def ^:private spincubes-cache
  "The last `[screen dims grid]` for `:spincubes`. The camera never moves, so
  the grid and the caption size depend on the screen alone."
  (atom nil))

(defn- spincubes-layout [m]
  (let [screen (:screen m)
        [cached-screen dims grid] @spincubes-cache]
    (if (= screen cached-screen)
      [dims grid]
      (let [dims (spincubes/dimensions m host-measure)
            grid (spincubes/grid-list (spincubes/camera dims) dims)]
        (reset! spincubes-cache [screen dims grid])
        [dims grid]))))

(defmethod draw-scene! :spincubes [_ state {:keys [m safe]}]
  (clear-to! spincubes/background-colour)
  (let [[dims grid] (spincubes-layout m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (spincubes/scene-list grid state dims))))
    (draw-caption! (:caption dims) spincubes/caption-colour)))
