(ns net.b12n.raylib-ios.scenes.wavecubes.draw
  "The draw-scene! method for the `:wavecubes` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.wavecubes :as wavecubes]))

(def ^:private wavecubes-cache
  "The last `[screen dims]` for `:wavecubes`. Its camera orbits, but the layout
  and the text size depend on the screen alone."
  (atom nil))

(defn- wavecubes-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @wavecubes-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (wavecubes/dimensions m host-measure)]
        (reset! wavecubes-cache [screen dims])
        dims))))

(defmethod draw-scene! :wavecubes [_ state {:keys [m safe]}]
  (clear-to! wavecubes/background-colour)
  (let [dims (wavecubes-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (wavecubes/scene-list state dims))))
    (draw-caption! (:caption dims) wavecubes/caption-colour)))
