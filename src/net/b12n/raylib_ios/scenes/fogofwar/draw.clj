(ns net.b12n.raylib-ios.scenes.fogofwar.draw
  "The draw-scene! method for the `:fogofwar` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.fogofwar :as fogofwar]))

(def ^:private fogofwar-dims-cache
  "The last `[screen dims]` for `:fogofwar`. Its text size needs a measure, which
  depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- fogofwar-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @fogofwar-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (fogofwar/dimensions m host-measure)]
        (reset! fogofwar-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :fogofwar [_ state {:keys [m]}]
  (clear-to! fogofwar/background-colour)
  (let [{:keys [scale ox oy tile-line hint-line]} (fogofwar-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        nx fogofwar/tiles-x
        ny fogofwar/tiles-y
        unit (* scale fogofwar/tile)
        ;; Tile edges, so neighbours share an edge to the pixel and no seam shows.
        xs (mapv #(int (+ ox (* % unit))) (range (inc nx)))
        ys (mapv #(int (+ oy (* % unit))) (range (inc ny)))
        shade-a (pack fogofwar/tile-a-colour)
        shade-b (pack fogofwar/tile-b-colour)
        outline (pack fogofwar/outline-colour)
        lw (max 1 (int (+ 0.5 scale)))
        tiles (:tiles state)]
    (dotimes [y ny]
      (let [y0 (nth ys y)
            h (- (nth ys (inc y)) y0)]
        (dotimes [x nx]
          (let [x0 (nth xs x)]
            (rl/draw-rectangle x0 y0 (- (nth xs (inc x)) x0) h
                               (if (zero? (nth tiles (+ x (* y nx)))) shade-a shade-b))))))
    ;; The original outlines each tile with DrawRectangleLines, one pixel just
    ;; inside its four edges. No rectangle-lines call is bound, and four
    ;; rectangles a tile is 1500 calls, so the same strips are drawn once across
    ;; the map: the left and right strip of every column of tiles, and the top
    ;; and bottom of every row.
    (let [top (nth ys 0)
          bottom (nth ys ny)
          left (nth xs 0)
          right (nth xs nx)]
      (dotimes [i (inc nx)]
        (let [x (nth xs i)]
          (when (< i nx) (rl/draw-rectangle x top lw (- bottom top) outline))
          (when (pos? i) (rl/draw-rectangle (- x lw) top lw (- bottom top) outline))))
      (dotimes [j (inc ny)]
        (let [y (nth ys j)]
          (when (< j ny) (rl/draw-rectangle left y (- right left) lw outline))
          (when (pos? j) (rl/draw-rectangle left (- y lw) (- right left) lw outline)))))
    (let [size (* scale fogofwar/player)]
      (rl/draw-rectangle (int (+ ox (* scale (:px state)))) (int (+ oy (* scale (:py state))))
                         (int size) (int size) (pack fogofwar/player-colour)))
    ;; The fog: a quad a tile, each corner the mean of the four tiles that meet
    ;; there, standing in for the 25 by 15 render texture the original stretches
    ;; with a bilinear filter. All 375 go in one rlgl batch of triangles, on the
    ;; fills' own whole-pixel edges, with the colour set only when it changes.
    (rl/rl-begin rl/RL-TRIANGLES)
    (fogofwar/emit-fog! (fogofwar/corner-alphas (:fog state)) xs ys
                        (fn [a] (rl/rl-color-4ub 0 0 0 a))
                        (fn [x y] (rl/rl-vertex-2f (double x) (double y))))
    (rl/rl-end)
    (let [text-c (pack fogofwar/text-colour)]
      (rl/draw-text (fogofwar/tile-text state) (int (:x tile-line)) (int (:y tile-line))
                    (int (:size tile-line)) text-c)
      (rl/draw-text (fogofwar/hint-text state) (int (:x hint-line)) (int (:y hint-line))
                    (int (:size hint-line)) text-c))))
