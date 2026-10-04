(ns net.b12n.raylib-ios.scenes.ortho.draw
  "The draw-scene! method for the `:ortho` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.ortho :as ortho]))

(def ^:private ortho-cache
  "The last `[[screen ortho?] dims grid]` for `:ortho`. The grid is the one for
  the mode being drawn, so the key holds both the screen and the mode."
  (atom nil))

(defn- ortho-layout [m state]
  (let [k [(:screen m) (boolean (:ortho? state))]
        [cached-key dims grid] @ortho-cache]
    (if (= k cached-key)
      [dims grid]
      (let [dims (ortho/dimensions m host-measure)
            grid (ortho/grid-list (ortho/camera state dims) dims)]
        (reset! ortho-cache [k dims grid])
        [dims grid]))))

(defmethod draw-scene! :ortho [_ state {:keys [m safe]}]
  (clear-to! ortho/background-colour)
  (let [[dims grid] (ortho-layout m state)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (ortho/scene-list grid state dims))))
    (draw-caption! (ortho/caption state dims) ortho/caption-colour)))
