(ns net.b12n.raylib-ios.scenes.dirbillboard.draw
  "The draw-scene! method for the `:dirbillboard` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.dirbillboard :as dirbillboard]))

(def ^:private dirbillboard-dims-cache
  "The last `[screen dims]` for `:dirbillboard`. The layout and the caption size
  depend on the screen alone."
  (atom nil))

(defn- dirbillboard-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @dirbillboard-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (dirbillboard/dimensions m host-measure)]
        (reset! dirbillboard-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :dirbillboard [_ state {:keys [m safe]}]
  (clear-to! dirbillboard/background-colour)
  (let [dims (dirbillboard-dims m)
        {:keys [s x y size]} (dirbillboard/caption state dims)
        [r g b a] dirbillboard/caption-colour]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (dirbillboard/scene-list state dims))))
    (rl/draw-text s (int x) (int y) (int size) (rl/rgba r g b a))))
