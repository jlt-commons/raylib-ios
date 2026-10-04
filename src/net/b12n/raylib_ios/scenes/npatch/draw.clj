(ns net.b12n.raylib-ios.scenes.npatch.draw
  "The draw-scene! method for the `:npatch` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.scenes.npatch :as npatch]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private npatch-cache
  "The last `[screen dims]` for `:npatch`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- npatch-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @npatch-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (npatch/dimensions m host-measure)]
        (reset! npatch-cache [screen dims])
        dims))))

(def ^:private npatch-spec (npatch/patch-spec))

(defmethod draw-scene! :npatch [_ state {:keys [m]}]
  (clear-to! npatch/background-colour)
  (let [dims (npatch-dims m)
        id (texture/id! :npatch :patch npatch-spec)
        [title _] (:lines dims)]
    (doseq [q (npatch/quads state dims)]
      (texture/quad! id q))
    (draw-caption! title npatch/title-colour)
    (draw-caption! (assoc (second (:lines dims)) :s (npatch/hint state)) npatch/hint-colour)
    (doseq [label (:labels dims)]
      (draw-caption! label npatch/hint-colour))
    (texture/quad! id (npatch/source-quad dims))))
