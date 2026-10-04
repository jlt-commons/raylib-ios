(ns net.b12n.raylib-ios.scenes.texpoly.draw
  "The draw-scene! method for the `:texpoly` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.texpoly :as texpoly]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private texpoly-cache
  "The last `[screen dims]` for `:texpoly`. Its caption size needs a measure,
  which depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- texpoly-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @texpoly-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (texpoly/dimensions m host-measure)]
        (reset! texpoly-cache [screen dims])
        dims))))

(def ^:private texpoly-spec (texpoly/wheel-spec))

(defmethod draw-scene! :texpoly [_ state {:keys [m]}]
  (clear-to! texpoly/background-colour)
  (let [dims (texpoly-dims m)
        id (texture/id! :texpoly :wheel texpoly-spec)
        [r g b a] texpoly/tint]
    (texture/triangles! id (texpoly/vertices state dims) (rl/rgba r g b a))
    (draw-caption! (first (:lines dims)) texpoly/title-colour)))
