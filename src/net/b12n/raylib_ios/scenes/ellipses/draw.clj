(ns net.b12n.raylib-ios.scenes.ellipses.draw
  "The draw-scene! method for the `:ellipses` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.ellipses :as ell]))

(defmethod draw-scene! :ellipses [_ {:keys [steer hit?]
                                     :as state} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack ell/background-colour))
        {:keys [thick rows]
         :as dims} (ell/dimensions m)
        edge (pack ell/outline-colour)
        dot (max 3.0 (* 1.5 thick))]
    (doseq [[k base] [[:a ell/a-colour] [:b ell/b-colour]]
            :let [[cx cy] (get state k)
                  {:keys [r n]} (get dims k)
                  fill (pack (if hit? ell/hit-colour base))]]
      ;; Each wedge goes through draw-triangle, which fixes its own winding.
      (doseq [[x1 y1 x2 y2 x3 y3] (ell/fan cx cy r n)]
        (rl/draw-triangle x1 y1 x2 y2 x3 y3 fill))
      (doseq [[x1 y1 x2 y2] (ell/outline cx cy r n)]
        (rl/draw-line-ex x1 y1 x2 y2 thick edge))
      (rl/draw-circle (int cx) (int cy) (float dot) edge))
    (doseq [[{:keys [x y size]} [s colour]] (map vector rows (ell/lines steer hit?))]
      (rl/draw-text s (int x) (int y) size (pack colour)))))
