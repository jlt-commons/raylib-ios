(ns net.b12n.raylib-ios.scenes.bgscroll.draw
  "The draw-scene! method for the `:bgscroll` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.bgscroll :as bgscroll]))

(def ^:private bgscroll-dims-cache
  "The last `[screen dims]` for `:bgscroll`. Its caption size needs a measure,
  which depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- bgscroll-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @bgscroll-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (bgscroll/dimensions m host-measure)]
        (reset! bgscroll-dims-cache [screen dims])
        dims))))

(defn- bgscroll-rect!
  [x y w h [r g b a]]
  (rl/draw-rectangle x y w h (rl/rgba r g b a)))

(defmethod draw-scene! :bgscroll [_ state {:keys [m]}]
  (clear-to! bgscroll/background-colour)
  (let [dims (bgscroll-dims m)]
    (bgscroll/emit-layers! bgscroll-rect! state dims)
    (draw-caption! (first (:lines dims)) bgscroll/caption-colour)))
