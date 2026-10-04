(ns net.b12n.raylib-ios.scenes.picking.draw
  "The draw-scene! method for the `:picking` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.picking :as picking]))

(def ^:private picking-cache
  "The last `[screen dims]` for `:picking`. Its camera moves, but the layout and
  the text size depend on the screen alone."
  (atom nil))

(defn- picking-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @picking-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (picking/dimensions m host-measure)]
        (reset! picking-cache [screen dims])
        dims))))

(defmethod draw-scene! :picking [_ state {:keys [m safe]}]
  (clear-to! (:background picking/colours))
  (let [dims (picking-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (picking/scene-list state dims))))
    (draw-caption! (:caption dims) (:hint picking/colours))
    (doseq [[{:keys [s colour]} slot] (map vector (picking/readout state) (:readout-slots dims))]
      (draw-caption! (assoc slot :s s) colour))))
