(ns net.b12n.raylib-ios.scenes.texcurve.draw
  "The draw-scene! method for the `:texcurve` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.texcurve :as texcurve]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private texcurve-cache
  "The last `[screen dims]` for `:texcurve`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- texcurve-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @texcurve-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (texcurve/dimensions m host-measure)]
        (reset! texcurve-cache [screen dims])
        dims))))

(def ^:private texcurve-spec (delay (texcurve/road-spec)))

(defmethod draw-scene! :texcurve [_ state {:keys [m]}]
  (clear-to! texcurve/background-colour)
  (let [dims (texcurve-dims m)
        id (texture/id! :texcurve :road @texcurve-spec)
        [r g b a] texcurve/tint
        [status hint] (:lines dims)]
    (texture/triangles! id (texcurve/vertices state dims) (rl/rgba r g b a))
    (draw-caption! (assoc status :s (texcurve/status-line (:width state) (:segments state))) texcurve/text-colour)
    (draw-caption! hint texcurve/text-colour)
    (doseq [k texcurve/button-keys
            :let [[x y w h] (get (:buttons dims) k)]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h)
                         (color (if (= k (:held state))
                                  texcurve/button-held-colour
                                  texcurve/button-colour)))
      (draw-caption! (get (:labels dims) k) texcurve/button-label-colour))))
