(ns net.b12n.raylib-ios.scenes.rawdata.draw
  "The draw-scene! method for the `:rawdata` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.rawdata :as rawdata]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private rawdata-cache
  "The last `[screen dims]` for `:rawdata`. Its text sizes need a measure, which
  depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- rawdata-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @rawdata-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (rawdata/dimensions m host-measure)]
        (reset! rawdata-cache [screen dims])
        dims))))

(def ^:private rawdata-checker (rawdata/checker-spec))

(defmethod draw-scene! :rawdata [_ state {:keys [m]}]
  (clear-to! rawdata/background-colour)
  (let [dims (rawdata-dims m)
        outline (color rawdata/outline-colour)
        [y0 rows] (rawdata/band (:frame state))
        ids {:checker (texture/id! :rawdata :checker rawdata-checker)
             :live (texture/band! :rawdata :live (rawdata/live-spec (:frame state)) y0 rows)}]
    (doseq [k rawdata/panel-keys]
      (texture/quad! (ids k) (rawdata/quad dims k))
      (doseq [[x y w h] (rawdata/outline-rects dims k)]
        (rl/draw-rectangle (int x) (int y) (int w) (int h) outline))
      (draw-caption! (get (:labels dims) k) rawdata/label-colour))
    (let [[head sub caption] (:lines dims)]
      (draw-caption! head rawdata/title-colour)
      (draw-caption! sub rawdata/subtitle-colour)
      (draw-caption! caption rawdata/caption-colour))))
