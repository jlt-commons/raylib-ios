(ns net.b12n.raylib-ios.scenes.rotcube.draw
  "The draw-scene! method for the `:rotcube` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.rotcube :as rotcube]))

;; --- rotcube, camera3d and ortho: software 3D -----------------------------------

(def ^:private rotcube-cache
  "The last `[screen dims grid]` for `:rotcube`. The grid and the caption size
  depend on the screen alone, so they are not rebuilt each frame."
  (atom nil))

(defn- rotcube-layout [m]
  (let [screen (:screen m)
        [cached-screen dims grid] @rotcube-cache]
    (if (= screen cached-screen)
      [dims grid]
      (let [dims (rotcube/dimensions m host-measure)
            grid (rotcube/grid-list (rotcube/camera dims) dims)]
        (reset! rotcube-cache [screen dims grid])
        [dims grid]))))

(defmethod draw-scene! :rotcube [_ state {:keys [m safe]}]
  (clear-to! rotcube/background-colour)
  (let [[dims grid] (rotcube-layout m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (rotcube/scene-list grid state dims))))
    (draw-caption! (:caption dims) rotcube/caption-colour)))
