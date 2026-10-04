(ns net.b12n.raylib-ios.scenes.boxcollide.draw
  "The draw-scene! method for the `:boxcollide` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.boxcollide :as boxcollide]))

(def ^:private boxcollide-cache
  "The last `[screen dims grid]` for `:boxcollide`. The camera never moves, so
  the layout, the text size and the grid depend on the screen alone."
  (atom nil))

(defn- boxcollide-layout [m]
  (let [screen (:screen m)
        [cached-screen dims grid] @boxcollide-cache]
    (if (= screen cached-screen)
      [dims grid]
      (let [dims (boxcollide/dimensions m host-measure)
            grid (boxcollide/grid-list (boxcollide/camera dims) dims)]
        (reset! boxcollide-cache [screen dims grid])
        [dims grid]))))

(defmethod draw-scene! :boxcollide [_ state {:keys [m safe]}]
  (clear-to! boxcollide/background-colour)
  (let [[dims grid] (boxcollide-layout m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (boxcollide/scene-list grid state dims))))
    (draw-caption! (assoc (:caption dims) :s (boxcollide/caption-text state))
                   (boxcollide/caption-colour state))))
