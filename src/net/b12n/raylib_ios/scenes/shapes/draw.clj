(ns net.b12n.raylib-ios.scenes.shapes.draw
  "The draw-scene! method for the `:shapes` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.outlines :as outl]
            [net.b12n.raylib-ios.scenes.shapes :as shp]))

(defmethod draw-scene! :shapes [_ _ {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack shp/background-colour))
        {:keys [title thick rect rect-outline circle circle-ring ellipse line triangle]}
        (shp/dimensions m)
        [rx ry rw rh] rect
        [ccx ccy cr] circle
        [rcx rcy rr] circle-ring
        [ecx ecy erx ery en] ellipse
        [lx1 ly1 lx2 ly2] line
        [tx1 ty1 tx2 ty2 tx3 ty3] triangle]
    (rl/draw-text (:s title) (int (:x title)) (int (:y title)) (:size title) (pack shp/title-colour))
    (rl/draw-rectangle (int rx) (int ry) (int rw) (int rh) (pack shp/rect-colour))
    (doseq [[x1 y1 x2 y2 th] (outl/rect-lines rect-outline thick)]
      (rl/draw-line-ex x1 y1 x2 y2 th (pack shp/rect-outline-colour)))
    (rl/draw-circle (int ccx) (int ccy) (float cr) (pack shp/circle-colour))
    ;; A ring rather than the bound one pixel draw-circle-lines, for legibility.
    (when-let [[inner outer] (outl/circle-ring rcx rcy rr thick)]
      (rl/draw-ring rcx rcy inner outer 0 360 shp/ring-segments (pack shp/ring-colour)))
    ;; Each wedge goes through draw-triangle, which fixes its own winding.
    (let [c (pack shp/ellipse-colour)]
      (doseq [[x1 y1 x2 y2 x3 y3] (shp/ellipse-fan ecx ecy erx ery en)]
        (rl/draw-triangle x1 y1 x2 y2 x3 y3 c)))
    (rl/draw-line-ex lx1 ly1 lx2 ly2 thick (pack shp/line-colour))
    (rl/draw-triangle tx1 ty1 tx2 ty2 tx3 ty3 (pack shp/triangle-colour))))
