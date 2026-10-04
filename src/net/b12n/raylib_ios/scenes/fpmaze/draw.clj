(ns net.b12n.raylib-ios.scenes.fpmaze.draw
  "The draw-scene! method for the `:fpmaze` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.fpmaze :as fpmaze]))

(def ^:private fpmaze-cache
  "The last `[screen dims static]` for `:fpmaze`: the layout and the minimap's
  panel and walls, which depend on the screen alone. The player's circle and
  line are built each frame."
  (atom nil))

(defn- fpmaze-layout [m]
  (let [screen (:screen m)
        [cached-screen dims static] @fpmaze-cache]
    (if (= screen cached-screen)
      [dims static]
      (let [dims (fpmaze/dimensions m host-measure)
            static (fpmaze/minimap-static dims)]
        (reset! fpmaze-cache [screen dims static])
        [dims static]))))

(defn- draw-minimap-item! [item thick]
  (case (first item)
    :rect (let [[_ x y w h c] item]
            (rl/draw-rectangle (int x) (int y) (int w) (int h) (color c)))
    :circle (let [[_ x y r c] item]
              (rl/draw-circle (int x) (int y) (double r) (color c)))
    :line (let [[_ x1 y1 x2 y2 c] item]
            (rl/draw-line-ex x1 y1 x2 y2 thick (color c)))))

(defmethod draw-scene! :fpmaze [_ state {:keys [m safe]}]
  (clear-to! fpmaze/background-colour)
  (let [[dims static] (fpmaze-layout m)
        thick (max 2.0 (/ (:cell-px dims) 9.0))]
    (draw-in-field! safe (:viewport dims)
                    (fn []
                      (rl/draw-3d! (fpmaze/scene-list state dims))
                      (doseq [item static] (draw-minimap-item! item thick))
                      (doseq [item (fpmaze/minimap-player state dims)] (draw-minimap-item! item thick))))
    (draw-caption! (:caption dims) fpmaze/caption-colour)))
