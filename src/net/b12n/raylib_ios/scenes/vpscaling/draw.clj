(ns net.b12n.raylib-ios.scenes.vpscaling.draw
  "The draw-scene! method for the `:vpscaling` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.vpscaling :as vpscaling]))

(def ^:private vpscaling-dims-cache
  "The last `[screen dims]` for `:vpscaling`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- vpscaling-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @vpscaling-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (vpscaling/dimensions m host-measure)]
        (reset! vpscaling-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :vpscaling [_ state {:keys [m safe]}]
  (clear-to! vpscaling/background-colour)
  (let [dims (vpscaling-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        text (fn [{:keys [s x y size]} colour]
               (rl/draw-text s (int x) (int y) (int size) (pack colour)))
        [wx wy ww wh] (vpscaling/window state dims)
        {:keys [dest source scale circle]} (vpscaling/plan state dims)
        [hx hy hw hh] (vpscaling/handle state dims)]
    (rl/draw-rectangle (int (- wx 2)) (int (- wy 2)) (int (+ ww 4)) (int (+ wh 4))
                       (pack vpscaling/frame-colour))
    (rl/draw-rectangle (int wx) (int wy) (int ww) (int wh) (pack vpscaling/window-colour))
    (when dest
      (let [[dx dy dw dh] dest
            [src-w src-h] source
            [kx ky] scale]
        ;; BeginScissorMode takes screen pixels, so the destination is moved by
        ;; the safe region's corner, and the safe region's own scissor is put
        ;; back afterwards because scissor does not nest. This is the render
        ;; texture's edge. The push, translate and scale stand in for the blit.
        (rl/begin-scissor-mode (int (+ (:x safe) dx)) (int (+ (:y safe) dy)) (int dw) (int dh))
        (try
          (rl/rl-push-matrix)
          (try
            (rl/rl-translatef (double dx) (double dy) 0.0)
            (rl/rl-scalef (double kx) (double ky) 1.0)
            (rl/draw-rectangle 0 0 (int src-w) (int src-h) (pack vpscaling/game-colour))
            (when circle
              (rl/draw-circle (int (first circle)) (int (second circle)) vpscaling/ball-radius
                              (pack vpscaling/ball-colour)))
            (finally (rl/rl-pop-matrix)))
          (finally
            (rl/end-scissor-mode)
            (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))))))
    (rl/draw-rectangle (int hx) (int hy) (int hw) (int hh) (pack vpscaling/handle-colour))
    (let [core (* 0.5 hw)]
      (rl/draw-rectangle (int (+ hx core)) (int (+ hy core)) (int core) (int core)
                         (pack vpscaling/handle-core-colour)))
    (doseq [k [:res-prev :res-next :type-prev :type-next]
            :let [[bx by bw bh] (k dims)]]
      (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack vpscaling/button-colour))
      (text (get-in dims [:arrows k]) vpscaling/button-label-colour))
    (doseq [line (vpscaling/readouts state dims)]
      (text line vpscaling/text-colour))))
