(ns net.b12n.raylib-ios.scenes.fpcamera.draw
  "The draw-scene! method for the `:fpcamera` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.fpcamera :as fpcamera]))

(def ^:private fpcamera-cache
  "The last `[screen dims]` for `:fpcamera`. Its camera moves, so only the
  layout and the text size are kept."
  (atom nil))

(defn- fpcamera-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @fpcamera-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (fpcamera/dimensions m host-measure)]
        (reset! fpcamera-cache [screen dims])
        dims))))

(defmethod draw-scene! :fpcamera [_ state {:keys [m safe]}]
  (clear-to! fpcamera/sky-colour)
  (let [dims (fpcamera-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (fpcamera/scene-list state dims))))
    (draw-caption! (:caption dims) fpcamera/caption-colour)))
