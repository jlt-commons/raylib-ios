(ns net.b12n.raylib-ios.scenes.splitscreen.draw
  "The draw-scene! method for the `:splitscreen` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.splitscreen :as split]))

(def ^:private splitscreen-dims-cache
  "The last `[screen dims]` for `:splitscreen`. Its banner sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- splitscreen-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @splitscreen-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (split/dimensions m host-measure)]
        (reset! splitscreen-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :splitscreen [_ state {:keys [m safe]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack split/outer-colour))
        dims (splitscreen-dims m)
        [p1 p2] (:players state)
        grid (pack split/grid-colour)
        background (pack split/background-colour)
        w (* split/cols split/cell)
        h (* split/rows split/cell)]
    (doseq [i [0 1]
            :let [[hx hy hw hh] (nth (:halves dims) i)
                  {:keys [rect s x y size]} (nth (:banners dims) i)
                  [bx by bw bh] rect]]
      ;; BeginScissorMode takes screen pixels, not scene pixels, so the half is
      ;; moved by the offset the gallery translates this scene by (the safe
      ;; region's corner). Scissor does not nest: it replaces the gallery's own, so
      ;; the safe region's is put back afterwards for the screen-space drawing.
      (rl/begin-scissor-mode (int (+ (:x safe) hx)) (int (+ (:y safe) hy)) (int hw) (int hh))
      (try
        (rl/draw-rectangle (int hx) (int hy) (int hw) (int hh) background)
        (rl/with-camera-2d
          (split/camera state dims i)
          (fn []
            (doseq [k (range (inc split/cols))]
              (rl/draw-line (* split/cell k) 0 (* split/cell k) h grid))
            (doseq [k (range (inc split/rows))]
              (rl/draw-line 0 (* split/cell k) w (* split/cell k) grid))
            ;; The original's size 10 labels, scaled with the world.
            (doseq [ci (range split/cols)
                    cj (range split/rows)]
              (rl/draw-text (str "[" ci "," cj "]")
                            (+ 10 (* split/cell ci))
                            (+ 15 (* split/cell cj))
                            10
                            grid))
            (rl/draw-rectangle (int (first p1)) (int (second p1)) split/cell split/cell
                               (pack (nth split/player-colours 0)))
            (rl/draw-rectangle (int (first p2)) (int (second p2)) split/cell split/cell
                               (pack (nth split/player-colours 1)))))
        (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack split/banner-colour))
        (rl/draw-text s x y size (pack (nth split/banner-text-colours i)))
        (finally
          (rl/end-scissor-mode)
          (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe)))))
    (let [[dx dy dw dh] (:divider dims)]
      (rl/draw-rectangle (int dx) (int dy) (int dw) (int dh) (pack split/divider-colour)))))
