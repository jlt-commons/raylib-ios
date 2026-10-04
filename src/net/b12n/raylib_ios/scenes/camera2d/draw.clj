(ns net.b12n.raylib-ios.scenes.camera2d.draw
  "The draw-scene! method for the `:camera2d` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.camera2d :as c2d]))

(def ^:private camera2d-dims-cache
  "The last `[screen dims]` for `:camera2d`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- camera2d-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @camera2d-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (c2d/dimensions m host-measure)]
        (reset! camera2d-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :camera2d [_ state {:keys [m safe]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack c2d/background-colour))
        dims (camera2d-dims m)
        camera (c2d/camera state dims)
        [fx fy fw fh] (:field dims)
        text (fn [{:keys [s x y size]} colour] (rl/draw-text s (int x) (int y) size (pack colour)))]
    ;; BeginScissorMode takes screen pixels, not scene pixels, so the field is
    ;; moved by the offset the gallery translates this scene by (the safe
    ;; region's corner). Scissor does not nest: it replaces the gallery's own, so
    ;; the safe region's is put back afterwards for the screen-space drawing.
    (rl/begin-scissor-mode (int (+ (:x safe) fx)) (int (+ (:y safe) fy)) (int fw) (int fh))
    (try
      (rl/with-camera-2d
        camera
        (fn []
          (rl/draw-rectangle -600 c2d/ground-y 2400 200 (pack c2d/ground-colour))
          (doseq [{:keys [x w h color]} c2d/buildings]
            (rl/draw-rectangle x (- c2d/ground-y h) w h (pack color)))
          (rl/draw-rectangle (int (- (:px state) 15)) (- c2d/ground-y 60) 30 60
                             (pack c2d/player-colour))))
      (finally
        (rl/end-scissor-mode)
        (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))))
    (let [cx (int (first (:offset dims)))]
      (rl/draw-line cx (int fy) cx (int (+ fy fh)) (pack c2d/centre-line-colour)))
    (text (:hint dims) c2d/hint-colour)
    (let [[x y w h] (:reset dims)
          label (:reset-label dims)]
      (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack c2d/button-colour))
      (text label c2d/button-label-colour))))
