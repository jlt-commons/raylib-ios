(ns net.b12n.raylib-ios.scenes.voxel.draw
  "The draw-scene! method for the `:voxel` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.voxel :as voxel]))

(def ^:private voxel-cache
  "The last `[screen dims]` for `:voxel`. Its camera moves with the player, so
  only the layout and the text sizes are kept."
  (atom nil))

(defn- voxel-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @voxel-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (voxel/dimensions m host-measure)]
        (reset! voxel-cache [screen dims])
        dims))))

(defmethod draw-scene! :voxel [_ state {:keys [m safe]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        colours voxel/colours
        dims (voxel-dims m)
        [bx by bw bh] (:button dims)
        [cx cy] (:crosshair dims)]
    (clear-to! (:background colours))
    (draw-in-field! safe (:viewport dims)
                    (fn []
                      (rl/draw-3d! (voxel/scene-list state dims))
                      ;; the original's crosshair: a RED dot of radius 4 on the ray
                      (rl/draw-circle (int cx) (int cy) 4.0 (pack (:crosshair colours)))))
    (draw-caption! (assoc (:caption dims) :s (voxel/voxel-text (count (:world state))))
                   (:caption colours))
    (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack (:button colours)))
    (draw-caption! (get (:labels dims) (:mode state)) (:button-label colours))))
