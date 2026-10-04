(ns net.b12n.raylib-ios.scenes.freecam.draw
  "The draw-scene! method for the `:freecam` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.freecam :as freecam]))

(def ^:private freecam-cache
  "The last `[screen dims]` for `:freecam`. Its camera moves, so only the layout
  and the HUD's measured text are kept."
  (atom nil))

(defn- freecam-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @freecam-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (freecam/dimensions m host-measure)]
        (reset! freecam-cache [screen dims])
        dims))))

(defmethod draw-scene! :freecam [_ state {:keys [m safe]}]
  (clear-to! freecam/background-colour)
  (let [dims (freecam-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        [hx hy hw hh] (:hud dims)
        [x0 y0 x1 y1] [(int hx) (int hy) (int (+ hx hw)) (int (+ hy hh))]
        edge (pack freecam/hud-edge-colour)
        [rx ry rw rh] (:reset dims)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (freecam/scene-list state dims))))
    ;; The original's translucent rect and its outline, then its help text.
    (rl/draw-rectangle x0 y0 (- x1 x0) (- y1 y0) (pack freecam/hud-fill-colour))
    (rl/draw-line x0 y0 x1 y0 edge)
    (rl/draw-line x1 y0 x1 y1 edge)
    (rl/draw-line x1 y1 x0 y1 edge)
    (rl/draw-line x0 y1 x0 y0 edge)
    (doseq [[k line] (map-indexed vector (:hud-lines dims))]
      (draw-caption! line (if (zero? k) freecam/hud-title-colour freecam/hud-text-colour)))
    (rl/draw-rectangle (int rx) (int ry) (int rw) (int rh) (pack freecam/button-colour))
    (draw-caption! (:reset-label dims) freecam/button-label-colour)))
