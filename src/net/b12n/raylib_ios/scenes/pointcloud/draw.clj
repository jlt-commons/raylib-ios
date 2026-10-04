(ns net.b12n.raylib-ios.scenes.pointcloud.draw
  "The draw-scene! method for the `:pointcloud` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.pointcloud :as pointcloud]))

(def ^:private pointcloud-cache
  "The last `[screen dims]` for `:pointcloud`. The camera never moves, so the
  layout and the text size depend on the screen alone."
  (atom nil))

(defn- pointcloud-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @pointcloud-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (pointcloud/dimensions m host-measure)]
        (reset! pointcloud-cache [screen dims])
        dims))))

(defmethod draw-scene! :pointcloud [_ state {:keys [m safe]}]
  (clear-to! pointcloud/background-colour)
  (let [dims (pointcloud-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (pointcloud/scene-list state dims))))
    (draw-caption! (:caption dims) pointcloud/caption-colour)))
