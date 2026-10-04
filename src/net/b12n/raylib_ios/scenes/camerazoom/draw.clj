(ns net.b12n.raylib-ios.scenes.camerazoom.draw
  "The draw-scene! method for the `:camerazoom` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.camerazoom :as czoom]))

(def ^:private camerazoom-dims-cache
  "The last `[screen dims]` for `:camerazoom`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- camerazoom-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @camerazoom-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (czoom/dimensions m host-measure)]
        (reset! camerazoom-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :camerazoom [_ state {:keys [m safe]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack czoom/background-colour))
        dims (camerazoom-dims m)
        camera (:camera state)
        [fx fy fw fh] (:field dims)
        text (fn [{:keys [s x y size]} colour] (rl/draw-text s (int x) (int y) size (pack colour)))
        lo (- (* czoom/cell (quot czoom/cells 2)))
        hi (+ lo (* czoom/cell czoom/cells))
        grid (pack czoom/grid-colour)]
    ;; BeginScissorMode takes screen pixels, not scene pixels, so the field is
    ;; moved by the offset the gallery translates this scene by (the safe
    ;; region's corner). Scissor does not nest: it replaces the gallery's own, so
    ;; the safe region's is put back afterwards for the screen-space drawing.
    (rl/begin-scissor-mode (int (+ (:x safe) fx)) (int (+ (:y safe) fy)) (int fw) (int fh))
    (try
      (rl/with-camera-2d
        camera
        (fn []
          ;; The grid is 22 lines each way, which draws the same picture as the
          ;; original's 441 outlined cells.
          (doseq [k (range (inc czoom/cells))
                  :let [v (+ lo (* k czoom/cell))]]
            (rl/draw-line v lo v hi grid)
            (rl/draw-line lo v hi v grid))
          (rl/draw-rectangle -20 -20 40 40 (pack czoom/square-colour))
          (rl/draw-circle 200 100 30.0 (pack czoom/circle-colour))
          (rl/draw-rectangle -300 150 120 60 (pack czoom/box-colour))
          (rl/draw-text "world origin" 30 30 20 (pack czoom/world-text-colour))))
      (finally
        (rl/end-scissor-mode)
        (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))))
    (let [[cx cy] (:cross state)
          arm (int (:size dims))
          cross (pack czoom/cross-colour)
          cx (int cx)
          cy (int cy)]
      (rl/draw-line (- cx arm) cy (+ cx arm) cy cross)
      (rl/draw-line cx (- cy arm) cx (+ cy arm) cross))
    (text (:hint dims) czoom/hint-colour)
    (text (assoc (:zoom-line dims) :s (czoom/zoom-label (:zoom camera))) czoom/zoom-colour)))
