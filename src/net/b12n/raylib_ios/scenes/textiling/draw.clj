(ns net.b12n.raylib-ios.scenes.textiling.draw
  "The draw-scene! method for the `:textiling` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.textiling :as textiling]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private textiling-cache
  "The last `[screen dims]` for `:textiling`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- textiling-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @textiling-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (textiling/dimensions m host-measure)]
        (reset! textiling-cache [screen dims])
        dims))))

(def ^:private textiling-spec (textiling/tile-spec))

(defmethod draw-scene! :textiling [_ state {:keys [m]}]
  (clear-to! textiling/background-colour)
  (let [dims (textiling-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        id (texture/id! :textiling :tile textiling-spec)
        [bx by bw bh] (:band dims)
        [title hint] (:lines dims)]
    (texture/quad! id (textiling/quad state dims))
    (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack textiling/band-colour))
    (draw-caption! (assoc title :s (textiling/title-line (:tiles state) (:aspect dims))) textiling/title-colour)
    (draw-caption! hint textiling/hint-colour)
    (doseq [k [:up :down]
            :let [[x y w h] (k dims)]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h)
                         (pack (if (= k (:held state))
                                 textiling/button-held-colour
                                 textiling/button-colour)))
      (draw-caption! (get (:labels dims) k) textiling/button-label-colour))))
