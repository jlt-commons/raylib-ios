(ns net.b12n.raylib-ios.scenes.srcrec.draw
  "The draw-scene! method for the `:srcrec` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.srcrec :as srcrec]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private srcrec-cache
  "The last `[screen dims]` for `:srcrec`. Its text size needs a measure, which
  depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- srcrec-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @srcrec-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (srcrec/dimensions m host-measure)]
        (reset! srcrec-cache [screen dims])
        dims))))

(def ^:private srcrec-spec (srcrec/sheet-spec))

(defmethod draw-scene! :srcrec [_ state {:keys [m]}]
  (clear-to! srcrec/background-colour)
  (let [dims (srcrec-dims m)
        id (texture/id! :srcrec :sheet srcrec-spec)
        line-c (color srcrec/line-colour)
        [vx1 vy1 vx2 vy2] (:vline dims)
        [hx1 hy1 hx2 hy2] (:hline dims)]
    (texture/quad! id (srcrec/quad state dims))
    (rl/draw-line vx1 vy1 vx2 vy2 line-c)
    (rl/draw-line hx1 hy1 hx2 hy2 line-c)
    (draw-caption! (first (:lines dims)) srcrec/text-colour)))
