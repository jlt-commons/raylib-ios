(ns net.b12n.raylib-ios.scenes.blendmodes.draw
  "The draw-scene! method for the `:blendmodes` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.blendmodes :as blendmodes]))

;; The blend scenes draw the part that is blended inside `call-blended!`, which
;; ends the mode in a `finally`, so a draw that throws cannot leave the next
;; scene (or the next frame's text) blending.
(def ^:private blendmodes-dims-cache
  "The last `[screen dims]` for `:blendmodes`. Its text size needs a measure,
  which depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- blendmodes-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @blendmodes-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (blendmodes/dimensions m host-measure)]
        (reset! blendmodes-dims-cache [screen dims])
        dims))))

(defn- blendmodes-rect!
  "One building or window: its colour is set by the caller's closure."
  [colour]
  (fn [x y w h] (rl/draw-rectangle x y w h colour)))

(defn- blendmodes-tri!
  "One triangle of a glow's fan: the centre in (r, g, b, 255), the rim clear.
  The caller has an `rl-begin` open."
  [x1 y1 x2 y2 x3 y3 r g b]
  (rl/rl-color-4ub r g b 255)
  (rl/rl-vertex-2f (double x1) (double y1))
  (rl/rl-color-4ub 0 0 0 0)
  (rl/rl-vertex-2f (double x2) (double y2))
  (rl/rl-vertex-2f (double x3) (double y3)))

(def ^:private blendmodes-colours
  "The scene's colours packed once."
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))]
    {:sky-top (pack blendmodes/sky-top)
     :sky-bottom (pack blendmodes/sky-bottom)
     :building (pack blendmodes/building-colour)
     :window (pack blendmodes/window-colour)
     :clear-glow (pack blendmodes/clear-glow)
     :text (pack blendmodes/text-colour)}))

(def ^:private blendmodes-building-rect! (blendmodes-rect! (:building blendmodes-colours)))

(def ^:private blendmodes-window-rect! (blendmodes-rect! (:window blendmodes-colours)))

(defmethod draw-scene! :blendmodes [_ state {:keys [m]}]
  (clear-to! blendmodes/background-colour)
  (let [dims (blendmodes-dims m)
        {:keys [ox oy pic-w pic-h lines]} dims
        x0 (int ox)
        y0 (int oy)
        w (- (int (+ ox pic-w)) x0)
        h (- (int (+ oy pic-h)) y0)
        [mode _] (blendmodes/mode-of state)
        {:keys [sky-top sky-bottom clear-glow text]} blendmodes-colours]
    ;; The skyline: the gradient, then the buildings, then the lit windows.
    (rl/draw-gradient-quad x0 y0 w h sky-top sky-top sky-bottom sky-bottom)
    (blendmodes/emit-buildings! blendmodes-building-rect! dims)
    (blendmodes/emit-windows! blendmodes-window-rect! dims)
    ;; The glow, blended. The clear quad is the texture's transparent black
    ;; outside the blobs; the fans are the three blobs.
    (blendmodes/call-blended!
     rl/begin-blend-mode rl/end-blend-mode mode
     (fn []
       (rl/draw-rectangle x0 y0 w h clear-glow)
       (rl/rl-begin rl/RL-TRIANGLES)
       (try
         (blendmodes/emit-glows! blendmodes-tri! dims)
         (finally (rl/rl-end)))))
    (let [[hint-l current-l] lines]
      (rl/draw-text (:s hint-l) (int (:x hint-l)) (int (:y hint-l)) (int (:size hint-l)) text)
      (rl/draw-text (blendmodes/current-line (:mode-idx state)) (int (:x current-l)) (int (:y current-l))
                    (int (:size current-l)) text))))
