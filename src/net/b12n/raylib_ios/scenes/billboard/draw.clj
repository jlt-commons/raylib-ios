(ns net.b12n.raylib-ios.scenes.billboard.draw
  "The draw-scene! method for the `:billboard` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.billboard :as billboard]))

(def ^:private billboard-dims-cache
  "The last `[screen dims]` for `:billboard`. The layout and the caption size
  depend on the screen alone."
  (atom nil))

(defn- billboard-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @billboard-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (billboard/dimensions m host-measure)]
        (reset! billboard-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :billboard [_ state {:keys [m safe]}]
  (clear-to! billboard/background-colour)
  (let [dims (billboard-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (billboard/scene-list state dims))))
    (draw-caption! (:caption dims) billboard/caption-colour)))
