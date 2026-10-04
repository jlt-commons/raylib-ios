(ns net.b12n.raylib-ios.scenes.platformer.draw
  "The draw-scene! method for the `:platformer` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.platformer :as platformer]))

(def ^:private platformer-dims-cache
  "The last `[screen dims]` for `:platformer`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- platformer-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @platformer-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (platformer/dimensions m host-measure)]
        (reset! platformer-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :platformer [_ state {:keys [m safe]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack platformer/background-colour))
        dims (platformer-dims m)
        camera (platformer/camera state dims)
        [fx fy fw fh] (:field dims)
        {:keys [x y]} (:player state)]
    ;; BeginScissorMode takes screen pixels, not scene pixels, so the field is
    ;; moved by the offset the gallery translates this scene by (the safe
    ;; region's corner). Scissor does not nest: it replaces the gallery's own, so
    ;; the safe region's is put back afterwards for the screen-space drawing.
    (rl/begin-scissor-mode (int (+ (:x safe) fx)) (int (+ (:y safe) fy)) (int fw) (int fh))
    (try
      (rl/with-camera-2d
        camera
        (fn []
          (doseq [[ex ey ew eh _ [r g b]] platformer/env-items]
            (rl/draw-rectangle (int ex) (int ey) (int ew) (int eh) (rl/rgba r g b 255)))
          ;; The player is a 40 by 40 square standing on its position.
          (rl/draw-rectangle (int (- x 20)) (int (- y 40)) 40 40 (pack platformer/player-colour))))
      (finally
        (rl/end-scissor-mode)
        (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))))
    (let [{:keys [s x y size]} (nth (:mode-lines dims) (:mode state))]
      (rl/draw-text s (int x) (int y) size (pack platformer/text-colour)))
    (doseq [{:keys [id rect label label-x label-y label-size]} (:buttons dims)
            :let [[bx by bw bh] rect]]
      (rl/draw-rectangle (int bx) (int by) (int bw) (int bh)
                         (pack (if (contains? (:held state) id)
                                 platformer/button-held-colour
                                 platformer/button-colour)))
      (rl/draw-text label label-x label-y label-size (pack platformer/button-label-colour)))))
