(ns net.b12n.raylib-ios.scenes.texcube.draw
  "The draw-scene! method for the `:texcube` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.texcube :as texcube]))

(def ^:private texcube-dims-cache
  "The last `[screen dims]` for `:texcube`. The layout and the caption size
  depend on the screen alone."
  (atom nil))

(defn- texcube-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @texcube-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (texcube/dimensions m host-measure)]
        (reset! texcube-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :texcube [_ state {:keys [m safe]}]
  (clear-to! texcube/background-colour)
  (let [dims (texcube-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (texcube/scene-list state dims))))
    (draw-caption! (:caption dims) texcube/caption-colour)))
