(ns net.b12n.raylib-ios.scenes.spheres.draw
  "The draw-scene! method for the `:spheres` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.spheres :as spheres]))

(def ^:private spheres-cache
  "The last `[screen dims]` for `:spheres`. The camera never moves, so the
  layout and the text size depend on the screen alone."
  (atom nil))

(defn- spheres-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @spheres-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (spheres/dimensions m host-measure)]
        (reset! spheres-cache [screen dims])
        dims))))

(defmethod draw-scene! :spheres [_ state {:keys [m safe]}]
  (clear-to! spheres/background-colour)
  (let [dims (spheres-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (spheres/scene-list state dims))))
    (draw-caption! (:caption dims) spheres/caption-colour)))
