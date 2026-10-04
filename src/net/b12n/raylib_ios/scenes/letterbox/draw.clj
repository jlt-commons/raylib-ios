(ns net.b12n.raylib-ios.scenes.letterbox.draw
  "The draw-scene! method for the `:letterbox` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.letterbox :as letterbox]))

(def ^:private letterbox-dims-cache
  "The last `[screen dims]` for `:letterbox`. Its text size needs a measure,
  which depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- letterbox-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @letterbox-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (letterbox/dimensions m host-measure)]
        (reset! letterbox-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :letterbox [_ state {:keys [m safe]}]
  (clear-to! letterbox/background-colour)
  (let [dims (letterbox-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        [wx wy ww wh] (letterbox/window state dims)
        {:keys [blit scale cross]} (letterbox/plan state dims)
        [bx by bw bh] blit
        [kx ky] scale
        pic (letterbox/picture state cross)
        [hx hy hw hh] (letterbox/handle state dims)]
    (rl/draw-rectangle (int (- wx 2)) (int (- wy 2)) (int (+ ww 4)) (int (+ wh 4))
                       (pack letterbox/frame-colour))
    (rl/draw-rectangle (int wx) (int wy) (int ww) (int wh) (pack letterbox/window-colour))
    ;; BeginScissorMode takes screen pixels, so the blit is moved by the safe
    ;; region's corner, and the safe region's own scissor is put back afterwards
    ;; because scissor does not nest. This is the render texture's edge. The
    ;; picture is drawn in its own 480 by 360 units under a push, translate and
    ;; scale, which is the blit.
    (when (and (pos? bw) (pos? bh))
      (rl/begin-scissor-mode (int (+ (:x safe) bx)) (int (+ (:y safe) by)) (int bw) (int bh))
      (try
        (rl/rl-push-matrix)
        (try
          (rl/rl-translatef (double bx) (double by) 0.0)
          (rl/rl-scalef (double kx) (double ky) 1.0)
          (rl/draw-rectangle 0 0 (:w pic) (:h pic) (pack (:clear pic)))
          (doseq [{:keys [x y w h colour]} (:blocks pic)]
            (rl/draw-rectangle x y w h (pack colour)))
          (let [{:keys [x y radius colour]} (:circle pic)]
            (rl/draw-circle x y (double radius) (pack colour)))
          (doseq [{:keys [s x y size colour]} (:texts pic)]
            (rl/draw-text s x y size (pack colour)))
          (doseq [[x1 y1 x2 y2] (:cross-lines pic)]
            (rl/draw-line-ex x1 y1 x2 y2 1.0 (pack letterbox/cross-colour)))
          (finally (rl/rl-pop-matrix)))
        (finally
          (rl/end-scissor-mode)
          (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe)))))
    (rl/draw-rectangle (int hx) (int hy) (int hw) (int hh) (pack letterbox/handle-colour))
    (let [core (* 0.5 hw)]
      (rl/draw-rectangle (int (+ hx core)) (int (+ hy core)) (int core) (int core)
                         (pack letterbox/handle-core-colour)))
    (doseq [{:keys [s x y size colour]} (letterbox/readouts state dims)]
      (rl/draw-text s (int x) (int y) (int size) (pack colour)))))
