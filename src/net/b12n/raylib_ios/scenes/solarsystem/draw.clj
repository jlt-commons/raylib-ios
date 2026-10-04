(ns net.b12n.raylib-ios.scenes.solarsystem.draw
  "The draw-scene! method for the `:solarsystem` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.solarsystem :as solarsystem]))

(def ^:private solarsystem-cache
  "The last `[screen dims]` for `:solarsystem`. The camera never moves, so the
  layout and the text size depend on the screen alone."
  (atom nil))

(defn- solarsystem-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @solarsystem-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (solarsystem/dimensions m host-measure)]
        (reset! solarsystem-cache [screen dims])
        dims))))

(defmethod draw-scene! :solarsystem [_ state {:keys [m safe]}]
  (clear-to! solarsystem/background-colour)
  (let [dims (solarsystem-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (solarsystem/scene-list state dims))))
    (draw-caption! (:caption dims) solarsystem/caption-colour)))
