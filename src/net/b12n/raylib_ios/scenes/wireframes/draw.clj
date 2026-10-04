(ns net.b12n.raylib-ios.scenes.wireframes.draw
  "The draw-scene! method for the `:wireframes` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.wireframes :as wireframes]))

(def ^:private wireframes-cache
  "The last `[screen dims]` for `:wireframes`."
  (atom nil))

(defn- wireframes-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @wireframes-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (wireframes/dimensions m host-measure)]
        (reset! wireframes-cache [screen dims])
        dims))))

(defmethod draw-scene! :wireframes [_ state {:keys [m safe]}]
  (clear-to! wireframes/background-colour)
  (let [dims (wireframes-dims m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (wireframes/scene-list state dims))))
    (draw-caption! (:caption dims) wireframes/caption-colour)))
