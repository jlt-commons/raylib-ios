(ns net.b12n.raylib-ios.scenes.worldscreen.draw
  "The draw-scene! method for the `:worldscreen` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.worldscreen :as worldscreen]))

(def ^:private worldscreen-cache
  "The last `[screen dims]` for `:worldscreen`. Its camera moves, so only the
  layout and the label's measured width are kept; both depend on the screen
  alone, since the text size comes from it."
  (atom nil))

(defn- worldscreen-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @worldscreen-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (worldscreen/dimensions m host-measure)]
        (reset! worldscreen-cache [screen dims])
        dims))))

(defmethod draw-scene! :worldscreen [_ state {:keys [m safe]}]
  (clear-to! worldscreen/background-colour)
  (let [dims (worldscreen-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (worldscreen/scene-list state dims))))
    (draw-caption! (:caption dims) worldscreen/caption-colour)
    (draw-caption! (worldscreen/readout state dims) worldscreen/readout-colour)
    (draw-caption! (worldscreen/label state dims) worldscreen/label-colour)))
