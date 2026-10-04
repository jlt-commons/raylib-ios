(ns net.b12n.raylib-ios.scenes.camera3d.draw
  "The draw-scene! method for the `:camera3d` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.camera3d :as c3d]))

(def ^:private camera3d-cache
  "The last `[screen dims]` for `:camera3d`. Its camera moves, so only the
  layout is kept."
  (atom nil))

(defn- camera3d-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @camera3d-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (c3d/dimensions m host-measure)]
        (reset! camera3d-cache [screen dims])
        dims))))

(defmethod draw-scene! :camera3d [_ state {:keys [m safe]}]
  (clear-to! c3d/background-colour)
  (let [dims (camera3d-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (c3d/scene-list state dims))))
    (draw-caption! (:caption dims) c3d/caption-colour)))
