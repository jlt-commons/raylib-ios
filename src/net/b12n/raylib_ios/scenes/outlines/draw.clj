(ns net.b12n.raylib-ios.scenes.outlines.draw
  "The draw-scene! method for the `:outlines` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.outlines :as outl]
            [net.b12n.raylib-ios.scenes.rounded :as rnd]))

(defmethod draw-scene! :outlines [_ {:keys [thick manual?]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack outl/background-colour))
        {:keys [k rows labels rect rounded ring]} (outl/dimensions m)
        t (* thick k)
        fill (pack outl/fill-colour)
        edge (pack outl/outline-colour)
        [rx ry rw rh] rect
        [ox oy ow oh] rounded
        [cx cy r] ring
        text [[(outl/hint-line manual?) outl/hint-colour]
              [(outl/thickness-line thick) outl/thickness-colour]
              [(first outl/notes) outl/hint-colour]
              [(second outl/notes) outl/hint-colour]]]
    (doseq [[{:keys [x y size]} [s colour]] (map vector rows text)]
      (rl/draw-text s (int x) (int y) size (pack colour)))
    (doseq [{:keys [s x y size]} labels]
      (rl/draw-text s (int x) (int y) size (pack outl/label-colour)))
    ;; The plain rectangle.
    (rl/draw-rectangle (int rx) (int ry) (int rw) (int rh) fill)
    (doseq [[x1 y1 x2 y2 th] (outl/rect-lines rect t)]
      (rl/draw-line-ex x1 y1 x2 y2 th edge))
    ;; The rounded one: filled with the rounded scene's own parts, then outlined.
    ;; The fill's corners are grown a degree, as in :rounded, so no seam of
    ;; background shows through it. Unlike :rounded it is not grown a pixel,
    ;; which would leave a faint rim outside the outline band.
    (let [rr (outl/rounded-radius rounded)
          {:keys [rects corners]} (rnd/parts {:x ox
                                              :y oy
                                              :rect-w ow
                                              :rect-h oh}
                                             rr)
          {:keys [lines rings]} (outl/rounded-outline rounded t)]
      (doseq [[x y w h] rects]
        (rl/draw-rectangle (int (Math/floor x)) (int (Math/floor y))
                           (int (Math/ceil w)) (int (Math/ceil h)) fill))
      (doseq [[ccx ccy start end] corners]
        (rl/draw-ring ccx ccy 0.0 rr (- start 1.0) (+ end 1.0) outl/corner-segments fill))
      (doseq [[x1 y1 x2 y2 th] lines]
        (rl/draw-line-ex x1 y1 x2 y2 th edge))
      ;; Half a degree past each quarter, which stays inside the straight band
      ;; beside it, so the arc and the line never leave a seam.
      (doseq [[ccx ccy inner outer start end] rings]
        (rl/draw-ring ccx ccy inner outer (- start 0.5) (+ end 0.5) outl/corner-segments edge)))
    ;; The ring is the only one that grows outward below zero.
    (rl/draw-circle (int cx) (int cy) (float r) fill)
    (when-let [[inner outer] (outl/circle-ring cx cy r t)]
      (rl/draw-ring cx cy inner outer 0 360 outl/ring-segments edge))))
